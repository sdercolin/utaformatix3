import type { UfData } from "npm:utaformatix-data@^1.1.0";
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
}
