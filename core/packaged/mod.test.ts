import * as uf from "./mod.ts";
import { expandGlob } from "jsr:@std/fs@^0.224.0/expand-glob";

const parserMap = [
  ["ccs", "parseCcs"],
  ["dv", "parseDv"],
  ["musicxml", "parseMusicXml"],
  ["ppsf", "parsePpsf"],
  ["s5p", "parseS5p"],
  ["standard.mid", "parseStandardMid"],
  ["svp", "parseSvp"],
  ["ust", "parseUst"],
  ["ustx", "parseUstx"],
  ["vocaloid.mid", "parseVocaloidMid"],
  ["vpr", "parseVpr"],
  ["vsq", "parseVsq"],
  ["vsqx", "parseVsqx"],
] as const satisfies [string, keyof typeof uf & `parse${string}`][];

for await (const file of expandGlob("./testAssets/**/*")) {
  const parser = parserMap.find(([ext]) => file.name.endsWith(ext));
  if (!parser) {
    console.log(`No parser found for ${file.name}`);
    continue;
  }

  const [, parse] = parser;
  const path = file.path.replace(`${import.meta.dirname}/testAssets`, ".");

  Deno.test(`parse: ${path} using ${parse}`, async () => {
    const data = await Deno.readFile(file.path);
    await uf[parse](data);
  });
}

const stdMidiData = await Deno.readFile("./testAssets/standard.mid");
const result = await uf.parseStandardMid(stdMidiData);

for (const name of [
  "generateCcs",
  "generateDv",
  "generateMusicXml",
  "generateStandardMid",
  "generateSvp",
  "generateUst",
  "generateUstx",
  "generateVocaloidMid",
  "generateVpr",
  "generateVsq",
  "generateVsqx",
] as const) {
  Deno.test(`generate: ${name}`, async () => {
    await uf[name](result);
  });
}
