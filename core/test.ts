import { copy } from "jsr:@std/fs@^0.224.0";
import { $ } from "jsr:@david/dax@0.41.0";
import { build, emptyDir } from "jsr:@deno/dnt@^0.41.1";

$.setPrintCommand(true);

const testPackageRoot =
  `${import.meta.dirname}/../build/temporary_test_package`;
const denoRoot = `${import.meta.dirname}/packaged`;

$.cd(denoRoot);

console.log("Testing on Deno...");
await $`deno test -A`;

console.log("Building and testing for npm...");
await emptyDir(testPackageRoot);
await build({
  entryPoints: ["./mod.ts"],
  outDir: testPackageRoot,
  shims: {
    deno: true,
  },
  package: {
    name: "utaformatix",
    version: "0.0.0",
  },
  async postBuild() {
    await copy(
      "./testAssets",
      testPackageRoot + "/esm/testAssets",
    );
  },
  scriptModule: false,
});
