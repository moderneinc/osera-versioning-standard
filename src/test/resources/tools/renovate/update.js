// Runs Renovate over a directory of build files and leaves its updates in place.
//
// Renovate's own `local` platform deliberately does not write anything back — its commitAndPush is
// a no-op — so this drives the layers underneath it instead: the manager that extracts
// dependencies, the lookup worker that decides what each one should become, and the manager's
// updateDependency that rewrites the declaration. That is the same code a Renovate PR comes from.
//
// Reads a JSON request on stdin. Writes nothing of consequence to stdout; the answer is the
// contents of the files themselves.

const fs = require('node:fs');
const path = require('node:path');

const req = (module) => require(path.join(process.env.RENOVATE_HOME, 'node_modules/renovate/dist', module));

async function main() {
  const request = JSON.parse(fs.readFileSync(0, 'utf8'));

  req('config/global').GlobalConfig.set({ localDir: request.dir, cacheDir: request.cacheDir });
  const manager = req(`modules/manager/${request.manager}/index.js`);
  const { lookupUpdates } = req('workers/repository/process/lookup/index.js');

  const log = [];
  for (const target of request.targets) {
    // Positions are recorded against the text as extracted, so anything already rewritten has to
    // be extracted again before the next target is applied.
    for (const packageFile of (await manager.extractAllPackageFiles({}, request.files)) ?? []) {
      for (const dep of packageFile.deps ?? []) {
        if ((dep.packageName ?? dep.depName) !== target.packageName) {
          continue;
        }
        // lookupUpdates is called directly, below the layer that applies Renovate's default
        // config, so a default only applies here if it is passed. This script therefore holds no
        // configuration of its own: every option comes from the column, which is the only way a
        // column claiming to represent Renovate's shipped defaults can be checked against them.
        const lookup = await lookupUpdates({
          ...dep,
          packageName: target.packageName,
          versioning: request.versioning,
          allowedVersions: target.allowedVersions,
          registryUrls: request.registryUrls,
          ...request.lookup,
        }).then(unwrap);

        // Renovate opens a pull request per update, so a lookup can return several. This applies
        // the first and records them all, because what decides a cell is whether any of them got there.
        const update = (lookup.updates ?? [])[0];
        const entry = { file: packageFile.packageFile, dep: target.packageName, skipReason: lookup.skipReason,
          update, offered: (lookup.updates ?? []).map((u) => ({ type: u.updateType, newValue: u.newValue })),
          dependency: dep };
        log.push(entry);
        if (!update) {
          continue;
        }
        const file = path.join(request.dir, packageFile.packageFile);
        const updated = manager.updateDependency({
          fileContent: fs.readFileSync(file, 'utf8'),
          upgrade: { ...dep, newValue: update.newValue, newVersion: update.newVersion },
        });
        entry.written = Boolean(updated);
        if (updated) {
          fs.writeFileSync(file, updated);
        }
      }
    }
  }
  process.stdout.write(JSON.stringify(log));
}

function unwrap(result) {
  return typeof result?.unwrapOrThrow === 'function' ? result.unwrapOrThrow() : result;
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
