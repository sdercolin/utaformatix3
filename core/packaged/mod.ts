import * as core from "./core.js";
import type { UfData } from "npm:utaformatix-data@^1.1.0";
import JSZip from "npm:jszip@^3.10.1";

const createSingleParse = (
  parse: (file: File) => Promise<core.DocumentContainer>,
  ext: string,
): SingleParseFunction =>
async (data): Promise<UfData> => {
  const result = await parse(
    data instanceof File ? data : new File([data], `data.${ext}`),
  );
  const ufData = await core.documentToUfData(result);
  return JSON.parse(ufData);
};

const createMultiParse = (
  parse: (files: File[]) => Promise<core.DocumentContainer>,
  ext: string,
): MultiParseFunction =>
async (...data): Promise<UfData> => {
  const files = data.map((d, i) =>
    d instanceof File ? d : new File([d], `data_${i}.${ext}`)
  );
  const result = await parse(files);
  const ufData = await core.documentToUfData(result);
  return JSON.parse(ufData);
};

const createSingleGenerate = (
  generate: (
    document: core.DocumentContainer,
  ) => Promise<core.ExportResult>,
): SingleGenerateFunction =>
async (data: UfData): Promise<Uint8Array> => {
  const project = await core.ufDataToDocument(JSON.stringify(data));
  const result = await generate(project);
  const arrayBuffer = await result.blob.arrayBuffer();
  return new Uint8Array(arrayBuffer);
};

const createUnzip =
  (generate: SingleGenerateFunction) =>
  async (data: UfData): Promise<Uint8Array[]> => {
    const zip = await generate(data);
    const zipReader = new JSZip();
    await zipReader.loadAsync(zip);
    const files = await Promise.all(
      Object.values(zipReader.files).map(
        async (file) =>
          [
            // {project name}_{track id}_{track name}.{ext}
            parseInt(
              file.name.replace(data.project.name + "_", "").split("_")[0],
              10,
            ),
            await file.async("uint8array"),
          ] as const,
      ),
    );
    files.sort(([a], [b]) => a - b);

    return files.map(([, data]) => data);
  };

type SingleParseFunction = (data: Uint8Array | File) => Promise<UfData>;
type MultiParseFunction = (...data: (Uint8Array | File)[]) => Promise<UfData>;
type SingleGenerateFunction = (data: UfData) => Promise<Uint8Array>;
type MultiGenerateFunction = (data: UfData) => Promise<Uint8Array[]>;

export type { UfData };

// Parse functions

/** Parse ccs (CeVIO's project) file */
export const parseCcs: SingleParseFunction = createSingleParse(
  core.parseCcs,
  "ccs",
);

/** Parse dv (DeepVocal's project) file */
export const parseDv: SingleParseFunction = createSingleParse(
  core.parseDv,
  "dv",
);

/** Parse MusicXML file */
export const parseMusicXml: SingleParseFunction = createSingleParse(
  core.parseMusicXml,
  "musicxml",
);

/** Parse ppsf (Piapro Studio's project) file */
export const parsePpsf: SingleParseFunction = createSingleParse(
  core.parsePpsf,
  "ppsf",
);

/** Parse s5p (Old Synthesizer V's project?) file */
export const parseS5p: SingleParseFunction = createSingleParse(
  core.parseS5p,
  "s5p",
);

/** Parse Standard MIDI file */
export const parseStandardMid: SingleParseFunction = createSingleParse(
  core.parseStandardMid,
  "mid",
);

/** Parse svp (Synthesizer V's project) file */
export const parseSvp: SingleParseFunction = createSingleParse(
  core.parseSvp,
  "svp",
);

/** Parse ust (UTAU's project) file */
export const parseUst: MultiParseFunction = createMultiParse(core.parseUst, "ust");

/** Parse ustx (OpenUtau's project) file */
export const parseUstx: SingleParseFunction = createSingleParse(
  core.parseUstx,
  "ustx",
);

/** Parse Vocaloid 1 MIDI file */
export const parseVocaloidMid: SingleParseFunction = createSingleParse(
  core.parseVocaloidMid,
  "mid",
);

/** Parse vpr (VOCALOID 5's project) file */
export const parseVpr: SingleParseFunction = createSingleParse(
  core.parseVpr,
  "vpr",
);

/** Parse vsq (VOCALOID 2's project) file */
export const parseVsq: SingleParseFunction = createSingleParse(
  core.parseVsq,
  "vsq",
);

/** Parse vsqx (VOCALOID 3/4's project) file */
export const parseVsqx: SingleParseFunction = createSingleParse(
  core.parseVsqx,
  "vsqx",
);

/** Map of extensions to parse functions */
// TODO: Get this from core (model.Format might be useful)
export const parseFunctions: Record<string, SingleParseFunction> = {
  ccs: parseCcs,
  dv: parseDv,
  musicxml: parseMusicXml,
  xml: parseMusicXml,
  ppsf: parsePpsf,
  s5p: parseS5p,
  mid: parseStandardMid,
  svp: parseSvp,
  ust: parseUst,
  ustx: parseUstx,
  vpr: parseVpr,
  vsq: parseVsq,
  vsqx: parseVsqx,
};

/** Parse a file based on its extension */
export const parseAny = async (file: File): Promise<UfData> => {
  const ext = file.name.split(".").pop()?.toLowerCase();
  if (!ext) throw new Error("No file extension");
  const parse = parseFunctions[ext];
  if (!parse) throw new Error(`Unsupported file extension: ${ext}`);
  const buffer = await file.arrayBuffer();
  return parse(new Uint8Array(buffer));
};

// Generate functions

/** Generate ccs (CeVIO's project) file */
export const generateCcs: SingleGenerateFunction = createSingleGenerate(
  core.generateCcs,
);

/** Generate dv (DeepVocal's project) file */
export const generateDv: SingleGenerateFunction = createSingleGenerate(
  core.generateDv,
);

/** Generate s5p (Old Synthesizer V's project?) file */
export const generateS5p: SingleGenerateFunction = createSingleGenerate(
  core.generateS5p,
);

/** Generate Standard MIDI file */
export const generateStandardMid: SingleGenerateFunction = createSingleGenerate(
  core.generateStandardMid,
);

/** Generate svp (Synthesizer V's project) file */
export const generateSvp: SingleGenerateFunction = createSingleGenerate(
  core.generateSvp,
);

/** Generate ustx (OpenUtau's project) file */
export const generateUstx: SingleGenerateFunction = createSingleGenerate(
  core.generateUstx,
);

/** Generate Vocaloid 1 MIDI file */
export const generateVocaloidMid: SingleGenerateFunction = createSingleGenerate(
  core.generateVocaloidMid,
);

/** Generate vpr (VOCALOID 5's project) file */
export const generateVpr: SingleGenerateFunction = createSingleGenerate(
  core.generateVpr,
);

/** Generate vsq (VOCALOID 2's project) file */
export const generateVsq: SingleGenerateFunction = createSingleGenerate(
  core.generateVsq,
);

/** Generate vsqx (VOCALOID 3/4's project) file */
export const generateVsqx: SingleGenerateFunction = createSingleGenerate(
  core.generateVsqx,
);

// Multi generate functions

/** Generate ust (UTAU's project) file. Returns an array of ust files, separated by tracks */
export const generateUst: MultiGenerateFunction = createUnzip(
  createSingleGenerate(core.generateUstZip),
);

/** Generate MusicXML file. Returns an array of MusicXML files, separated by tracks */
export const generateMusicXml: MultiGenerateFunction = createUnzip(
  createSingleGenerate(core.generateMusicXmlZip),
);
