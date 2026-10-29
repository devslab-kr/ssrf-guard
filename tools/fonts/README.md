# DDS documentation fonts

The only font source is the private `@devslab/family-fonts` artifact produced by DDS. The vendored archive is pinned by SHA256 and SHA512 integrity in `artifact.json`.

`python -m mkdocs build --strict` invokes the MkDocs hook before file discovery. The Node adapter verifies the archive, rejects unsafe tar entries, and runs the package exporter. It contains no font faces, family stacks, preload lists, or font copies.

Generated assets, head partial and temporary extraction files are ignored. Update the shared artifact and its verified pin together; do not edit generated font definitions or add service-specific fonts.

Run `node --test tools/fonts/prepare.test.mjs` for the archive integrity and safe-extraction contract.
