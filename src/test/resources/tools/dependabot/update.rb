# Runs Dependabot over a directory of build files and leaves its updates in place.
#
# dependabot-core's own entry point expects a git host and opens pull requests, so this drives the
# libraries underneath it: the file parser, the update checker that decides whether a newer version
# exists, and the file updater that rewrites the declaration. That is the same code a Dependabot
# pull request comes from.
#
# Reads a JSON request on stdin. The answer is the contents of the files themselves.

require "json"
require "dependabot/maven"
require "dependabot/gradle"
require "dependabot/npm_and_yarn"
require "dependabot/source"
require "dependabot/credential"
require "dependabot/dependency_file"

ECOSYSTEMS = {
  "maven" => [Dependabot::Maven::FileParser, Dependabot::Maven::UpdateChecker, Dependabot::Maven::FileUpdater],
  "gradle" => [Dependabot::Gradle::FileParser, Dependabot::Gradle::UpdateChecker, Dependabot::Gradle::FileUpdater],
  "npm_and_yarn" => [Dependabot::NpmAndYarn::FileParser, Dependabot::NpmAndYarn::UpdateChecker,
                     Dependabot::NpmAndYarn::FileUpdater]
}.freeze

request = JSON.parse($stdin.read)
dir = request.fetch("dir")
parser_class, checker_class, updater_class = ECOSYSTEMS.fetch(request.fetch("ecosystem"))

source = Dependabot::Source.new(provider: "github", repo: "finos/osera", directory: "/")
# npm registries are declared differently from Maven ones, and the scheme has to be spelled out:
# Package::RegistryFinder prepends https:// to anything not starting with http, and a plain host
# then fails as PrivateSourceTimedOut rather than as a configuration error.
credentials = request.fetch("registryUrls").map do |url|
  if request.fetch("ecosystem") == "npm_and_yarn"
    Dependabot::Credential.new({ "type" => "npm_registry", "registry" => url.sub(%r{/\z}, ""),
                                 "replaces-base" => true })
  else
    Dependabot::Credential.new({ "type" => "maven_repository", "url" => url })
  end
end

def read(dir, names)
  names.map { |name| Dependabot::DependencyFile.new(name: name, content: File.read(File.join(dir, name))) }
end

log = []
request.fetch("targets").each do |target|
  files = read(dir, request.fetch("files"))
  dependency = parser_class.new(dependency_files: files, source: source, credentials: credentials).parse
                           .find { |d| d.name == target.fetch("packageName") }
  next unless dependency

  checker = checker_class.new(
    dependency: dependency,
    dependency_files: files,
    credentials: credentials,
    # Dependabot has no way to say "only versions matching the producer's pattern", only which
    # versions to ignore, so each caller says what to rule out: the JVM columns rule out anything
    # above the producer's patch, and the npm ones rule out anything off the line being patched.
    ignored_versions: target.fetch("ignoredVersions")
  )
  entry = {
    "dep" => dependency.name,
    "current" => dependency.version,
    "latest" => checker.latest_version&.to_s,
    "can_update" => checker.can_update?(requirements_to_unlock: :own)
  }
  log << entry
  next unless entry["can_update"]

  updater_class.new(
    dependencies: checker.updated_dependencies(requirements_to_unlock: :own),
    dependency_files: files,
    credentials: credentials
  ).updated_dependency_files.each do |file|
    File.write(File.join(dir, file.name), file.content)
  end
end

$stdout.write(JSON.generate(log))
