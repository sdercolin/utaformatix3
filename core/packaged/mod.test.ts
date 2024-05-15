import * as uf from "./mod.ts";
import { expandGlob } from "jsr:@std/fs@^0.224.0/expand-glob";
import { assertEquals } from "jsr:@std/assert@^0.224.0";
const testAssetsDir = `${import.meta.dirname}/testAssets`;

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
  const path = file.path.replace(testAssetsDir, ".");

  Deno.test(`parse: ${path} using ${parse}`, async () => {
    const data = await Deno.readFile(file.path);
    await uf[parse](data);
  });
}

Deno.test("parse: MultipleGenerateFunctions can serialize score with correct order", async () => {
  const with10Tracks = await Deno.readFile(testAssetsDir + "/10tracks.svp");
  const ufdata = await uf.parseSvp(with10Tracks);

  const usts = await uf.generateUst(ufdata);
  const noteNums = usts.map((ust) => {
    const decoded = new TextDecoder().decode(ust);
    const noteNum = decoded.match(/NoteNum=(\d+)/g);
    if (!noteNum) {
      throw new Error("No NoteNum found");
    }
    return parseInt(noteNum[0].split("=")[1]);
  });

  assertEquals(
    noteNums,
    // C4 to D5
    [60, 62, 64, 65, 67, 69, 71, 72, 74, 76],
  );
});

for (
  const name of [
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
  ] as const
) {
  Deno.test(`generate: ${name}`, async () => {
    const stdMidiData = await Deno.readFile(testAssetsDir + "/standard.mid");
    const result = await uf.parseStandardMid(stdMidiData);

    await uf[name](result);
  });
}
