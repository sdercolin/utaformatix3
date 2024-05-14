<!-- TODO: replace sevenc-nanashi/utaformatix-ts with actual package -->

# Utaformatix3 for TypeScript

[![JSR](https://jsr.io/badges/@sevenc-nanashi/utaformatix-ts)](https://jsr.io/@sevenc-nanashi/utaformatix-ts)

UtaFormatix is an library for parsing, generating, and converting project files among singing voice synthesizer softwares.

## Usage

`parse[format]` converts a binary file to [UtaFormatix Data](https://github.com/sdercolin/utaformatix-data).

```typescript
import * as uf from "jsr:@sevenc-nanashi/utaformatix-ts";

const stdMidiData = await Deno.readFile("./standard.mid");
const result: UfData = await uf.parseStandardMid(stdMidiData);

console.log(result); // { formatVersion: 1, project: { ... } }

// Exceptionally, parseUst allows multiple files to be passed. Each file represents a track.

const firstTrack = await Deno.readFile("./first_track.ust");
const secondTrack = await Deno.readFile("./second_track.ust");

const result2: UfData = await uf.parseUst(firstTrack, secondTrack);
```

`generate[format]` converts UtaFormatix Data to a binary file.

```typescript
import * as uf from "jsr:@sevenc-nanashi/utaformatix-ts";

const ufdata = JSON.parse(await Deno.readFile("./standard.ufdata.json"));

const midResult: Uint8Array = await uf.generateStandardMid(ufdata);
await Deno.writeFile("./standard.mid", midResult);

// generateUst and generateMusicXml returns multiple files. Each file represents a track.
const musicXmlResult: Uint8Array[] = await uf.generateMusicXml(ufdata);
await Deno.writeFile("./first_track.musicxml", musicXmlResult[0]);
await Deno.writeFile("./second_track.musicxml", musicXmlResult[1]);
```

## Installation

Please follow [jsr documentation](https://jsr.io/docs/using-packages) for
installation instructions.

```bash
# Deno
deno add @sevenc-nanashi/utaformatix-ts

# Node.js (one of the below, depending on your package manager)
# This package requires Node.js 20 or later.
npx jsr add @sevenc-nanashi/utaformatix-ts
yarn dlx jsr add @sevenc-nanashi/utaformatix-ts
pnpm dlx jsr add @sevenc-nanashi/utaformatix-ts

# Bun is not supported yet (segmentation fault)
# bunx jsr add @sevenc-nanashi/utaformatix-ts
```

## License

This project is licensed under Apache License 2.0. See
[LICENSE](https://github.com/sdercolin/utaformatix3/blob/master/LICENSE.md) for more
information.
