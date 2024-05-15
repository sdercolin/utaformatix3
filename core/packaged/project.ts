import type { UfData } from "npm:utaformatix-data@^1.1.0";
import * as base from "./base.ts";
type BaseProject = UfData["project"];

/**
 * Project data.
 *
 * > [!NOTE]
 * > This class is based on UtaFormatix data (v1).
 * > Please refer to the [UtaFormatix Data Document](https://github.com/sdercolin/utaformatix-data?tab=readme-ov-file#data-structure)
 * > for more information.
 */
export class Project implements BaseProject {
  /** Constructs a new project data from UtaFormatix data. */
  constructor(public data: UfData) {
  }

  /** Converts the project data to UtaFormatix data. */
  toUfData(): UfData {
    return structuredClone(this.data);
  }

  /** Count of measure prefixes (measures that cannot contain notes, restricted by some editors) */
  get measurePrefix(): BaseProject["measurePrefix"] {
    return this.data.project.measurePrefix;
  }

  /** Project name */
  get name(): BaseProject["name"] {
    return this.data.project.name;
  }

  /** Tempo changes */
  get tempos(): BaseProject["tempos"] {
    return this.data.project.tempos;
  }

  /** Time signature changes */
  get timeSignatures(): BaseProject["timeSignatures"] {
    return this.data.project.timeSignatures;
  }

  /** Tracks */
  get tracks(): BaseProject["tracks"] {
    return this.data.project.tracks;
  }

  /** Creates a Project instance from ccs (CeVIO's project file) file. */
  static async fromCcs(data: Uint8Array | File): Promise<Project> {
    return new Project(await base.parseCcs(data));
  }

  /** Creates a Project instance from dv (DeepVocal's project file) file. */
  static async fromDv(data: Uint8Array | File): Promise<Project> {
    return new Project(await base.parseDv(data));
  }

  /** Creates a Project instance from MusicXML file file. */
  static async fromMusicXml(data: Uint8Array | File): Promise<Project> {
    return new Project(await base.parseMusicXml(data));
  }

  /** Creates a Project instance from ppsf (Piapro Studio's project file) file. */
  static async fromPpsf(data: Uint8Array | File): Promise<Project> {
    return new Project(await base.parsePpsf(data));
  }

  /** Creates a Project instance from s5p (Old Synthesizer V's project file) file. */
  static async fromS5p(data: Uint8Array | File): Promise<Project> {
    return new Project(await base.parseS5p(data));
  }

  /** Creates a Project instance from Standard MIDI file. */
  static async fromStandardMid(data: Uint8Array | File): Promise<Project> {
    return new Project(await base.parseStandardMid(data));
  }

  /** Creates a Project instance from svp (Synthesizer V's project file) file. */
  static async fromSvp(data: Uint8Array | File): Promise<Project> {
    return new Project(await base.parseSvp(data));
  }

  /** Creates a Project instance from ust (UTAU's project file) file. */
  static async fromUst(data: Uint8Array | File): Promise<Project> {
    return new Project(await base.parseUst(data));
  }

  /** Creates a Project instance from ustx (OpenUtau's project file) file. */
  static async fromUstx(data: Uint8Array | File): Promise<Project> {
    return new Project(await base.parseUstx(data));
  }

  /** Creates a Project instance from Vocaloid 1 MIDI file. */
  static async fromVocaloidMid(data: Uint8Array | File): Promise<Project> {
    return new Project(await base.parseVocaloidMid(data));
  }

  /** Creates a Project instance from vpr (VOCALOID 5's project file) file. */
  static async fromVpr(data: Uint8Array | File): Promise<Project> {
    return new Project(await base.parseVpr(data));
  }

  /** Creates a Project instance from vsq (VOCALOID 2's project file) file. */
  static async fromVsq(data: Uint8Array | File): Promise<Project> {
    return new Project(await base.parseVsq(data));
  }

  /** Creates a Project instance from vsqx (VOCALOID 3/4's project file) file. */
  static async fromVsqx(data: Uint8Array | File): Promise<Project> {
    return new Project(await base.parseVsqx(data));
  }

  /** Creates a Project instance from file, based on the file extension. */
  static async fromAny(file: File): Promise<Project> {
    return new Project(await base.parseAny(file));
  }

  /** Generates ccs (CeVIO's project file) file from the project. */
  static toCcs(project: Project): Promise<Uint8Array> {
    return base.generateCcs(project.data);
  }

  /** Generates dv (DeepVocal's project file) file from the project. */
  static toDv(project: Project): Promise<Uint8Array> {
    return base.generateDv(project.data);
  }

  /** Generates s5p (Old Synthesizer V's project file) file from the project. */
  static toS5p(project: Project): Promise<Uint8Array> {
    return base.generateS5p(project.data);
  }

  /** Generates Standard MIDI file from the project. */
  static toStandardMid(project: Project): Promise<Uint8Array> {
    return base.generateStandardMid(project.data);
  }

  /** Generates svp (Synthesizer V's project file) file from the project. */
  static toSvp(project: Project): Promise<Uint8Array> {
    return base.generateSvp(project.data);
  }

  /** Generates ustx (OpenUtau's project file) file from the project. */
  static toUstx(project: Project): Promise<Uint8Array> {
    return base.generateUstx(project.data);
  }

  /** Generates Vocaloid 1 MIDI file from the project. */
  static toVocaloidMid(project: Project): Promise<Uint8Array> {
    return base.generateVocaloidMid(project.data);
  }

  /** Generates vpr (VOCALOID 5's project file) file from the project. */
  static toVpr(project: Project): Promise<Uint8Array> {
    return base.generateVpr(project.data);
  }

  /** Generates vsq (VOCALOID 2's project file) file from the project. */
  static toVsq(project: Project): Promise<Uint8Array> {
    return base.generateVsq(project.data);
  }

  /** Generates vsqx (VOCALOID 3/4's project file) file from the project. */
  static toVsqx(project: Project): Promise<Uint8Array> {
    return base.generateVsqx(project.data);
  }

  /**
   * Generates ust (UTAU's project file) files from the project.
   * Returns an array of ust files, separated by tracks.
   */
  static toUst(project: Project): Promise<Uint8Array[]> {
    return base.generateUst(project.data);
  }

  /**
   * Generates MusicXML files from the project.
   * Returns an array of MusicXML files, separated by tracks.
   */
  static toMusicXml(project: Project): Promise<Uint8Array[]> {
    return base.generateMusicXml(project.data);
  }
}
