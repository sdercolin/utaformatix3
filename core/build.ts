import { build, Plugin } from "npm:esbuild@^0.21.2";
import { copy, expandGlob } from "jsr:@std/fs@0.229.0";
import { $ } from "jsr:@david/dax@0.41.0";
import { relative, SEPARATOR } from "jsr:@std/path@^0.221.0";

$.setPrintCommand(true);

const buildDir = `${import.meta.dirname}/../build/js`;
const resourceDir = `${import.meta.dirname}/src/main/resources`;
const unbundled = `${buildDir}/temporary_unbundled`;
const packageDir = `${import.meta.dirname}/packaged`;
const modifiedCorePath = `${unbundled}/kotlin/utaformatix-core-modified.mjs`;

let shouldCopy = false;
if (await Deno.stat(`${unbundled}/package.json`).catch(() => null)) {
  const unbundledLastModified = await Deno.stat(`${unbundled}/package.json`);
  const coreLastModified = await Deno.stat(
    `${buildDir}/packages/utaformatix-core/package.json`,
  );
  if (coreLastModified.mtime! > unbundledLastModified.mtime!) {
    console.log("Core has been modified, copying...");
    await Deno.remove(unbundled, { recursive: true });
    shouldCopy = true;
  } else {
    console.log("Core has not been modified, skipping...");
  }
} else {
  console.log("Core has not been copied, copying...");
  shouldCopy = true;
}
if (shouldCopy) {
  await Deno.mkdir(unbundled, { recursive: true });

  await copy(
    `${buildDir}/packages/utaformatix-core/package.json`,
    `${unbundled}/package.json`,
  );
  await copy(
    `${buildDir}/packages/utaformatix-core/kotlin`,
    `${unbundled}/kotlin`,
  );
}

// Add some polyfills for more portability

// xmldom on npm doesn't export internals such as Document and Element yet, so
// the version on GitHub is used instead
// For later contributors: if xmldom is updated to export these, please update
$.cd(unbundled);
await $`npm install xmldom/xmldom#d55f8a7 stream-browserify@3.0.0`;

const baseCore = await Deno.readTextFile(
  `${unbundled}/kotlin/utaformatix-core.mjs`,
);
await Deno.writeTextFile(
  modifiedCorePath,
  baseCore.replace(
    "//region block: imports",
    await Deno.readTextFile(`${import.meta.dirname}/polyfills.mjs`),
  ),
);

// Make a plugin for esbuild to handle the resources
const resources = await Array.fromAsync(
  expandGlob(`${resourceDir}/**/*`),
);
const resourcePlugin: Plugin = {
  name: "resource",
  setup(build) {
    for (const resource of resources) {
      const resourcePath = "./" +
        relative(resourceDir, resource.path).replaceAll(
          SEPARATOR,
          "/",
        );
      build.onResolve({
        filter: new RegExp(`^${resourcePath.replaceAll(".", "\\.")}$`),
      }, () => ({
        path: resource.path,
        namespace: "resource",
      }));
    }
    build.onLoad({ filter: /.*/, namespace: "resource" }, async (args) => ({
      contents: `export default ${
        JSON.stringify(await Deno.readTextFile(args.path))
      }`,
    }));
  },
};

console.log("Bundling core...");
await build({
  entryPoints: [modifiedCorePath],
  bundle: true,
  format: "esm",
  outfile: `${packageDir}/core.js`,
  plugins: [resourcePlugin],
  minify: true,
  banner: {
    js: `// deno-lint-ignore-file\n` +
      `/// <reference types="./core.d.ts" />\n`,
  },
  alias: {
    "stream": `${unbundled}/node_modules/stream-browserify/index.js`,
  },
});
await copy(
  `${unbundled}/kotlin/utaformatix-core.d.ts`,
  `${packageDir}/core.d.ts`,
  { overwrite: true },
);

console.log("Done!");
