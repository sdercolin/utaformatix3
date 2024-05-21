const path = require("path");
const { globSync } = require("glob");

const resourcePath = path.resolve(
  __dirname,
  "..",
  "utaformatix-core",
  "kotlin",
);
const resources = globSync(
  resourcePath + "/**/*",
).map((resource) =>
  "." + resource.replace(
    resourcePath,
    "",
  ).replace(/\\/g, "/")
);
if (resources.length === 0) throw new Error(`No resources found in ${resourcePath}`);

class MyResolverPlugin {
  apply(resolver) {
    const target = resolver.ensureHook("resolve");
    resolver
      .getHook("resolve")
      .tapAsync("MyResolverPlugin", (request, resolveContext, callback) => {
        if (
          resources.includes(request.request) &&
          !request.path.startsWith(resourcePath)
        ) {
          return resolver.doResolve(
            target,
            {
              ...request,
              path: resourcePath,
              request: request.request,
            },
            null,
            resolveContext,
            callback,
          );
        }
        callback();
      });
  }
}

config.resolve.plugins = [new MyResolverPlugin()];
