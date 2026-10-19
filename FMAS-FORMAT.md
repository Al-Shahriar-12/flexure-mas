# The .fmas file format (schema 1)

A `.fmas` file is a UTF-8 JSON document with a MAS header used by every MAS Studio app. All values are stored in base SI units, whatever units the author was viewing, so a file opens identically everywhere.

## Header

| Field | Value |
| --- | --- |
| `format` | Always `"MAS"` |
| `app` | `"flexure"` for Flexure MAS beam files |
| `appName`, `studio` | `"Flexure MAS"`, `"MAS Studio"` (informational) |
| `kind` | `"beam"` |
| `schema` | `1`. Readers must refuse files with a higher schema |
| `savedAt` | ISO 8601 time the file was written |
| `units.base` | Always `"SI-kN-m"` in schema 1 |
| `units.display` | Units the author was using: `kN-m`, `N-mm`, `kip-ft`, `kip-in` or `lb-ft` |
| `project.title` | Project name, up to 80 characters |
| `settings.momentDiagram` | `"up"` or `"tension"` |
| `settings.sectionCut` | Section cut position in m |

## Model (`model`)

| Field | Unit | Meaning |
| --- | --- | --- |
| `L` | m | Beam length |
| `material` | — | `steel`, `concrete`, `aluminium`, `timber` or `custom` |
| `E` | GPa | Elastic modulus |
| `nu` | — | Poisson’s ratio |
| `rho` | kg/m³ | Density (used for self-weight) |
| `selfWeight` | — | `true` to add self-weight as a downward UDL |
| `section.type` | — | `I`, `rect`, `circle`, `pipe` or `custom` |
| `section.d, bf, tf, tw` | mm | I-section depth, flange width, flange thickness, web thickness |
| `section.b, h` | mm | Rectangle width and depth |
| `section.D, t` | mm | Circle or pipe outside diameter; pipe wall thickness |
| `section.A` | mm² | Custom area |
| `section.Iz, Iy, J` | ×10⁶ mm⁴ | Custom second moments of area and torsion constant |
| `section.depth, width` | mm | Custom extreme-fibre dimensions for stresses |

### Supports (`model.supports[]`)
`{ "type": "fixed" | "pin" | "roller" | "guided" | "hinge", "x": metres from the left end }`

### Loads (`model.loads[]`)

| `type` | Fields | `dir` values |
| --- | --- | --- |
| `point` | `P` (kN), `x` (m) | `-y`, `+y`, `-z`, `+z`, `-x`, `+x` |
| `udl` | `w` (kN/m), `x1`, `x2` (m) | same as point |
| `uvl` | `w1` at `x1`, `w2` at `x2` (kN/m) | same as point |
| `moment` | `M` (kN·m), `x` (m) | `ccw`, `cw` (about Z); `my+`, `my-` (about Y) |
| `torque` | `T` (kN·m), `x` (m) | `+x`, `-x` |

Older files may contain `{"type": "axial", "dir": "+x" | "-x", "P", "x"}`; readers treat it as a point load in X.

## Example

```json
{
  "format": "MAS", "app": "flexure", "appName": "Flexure MAS", "studio": "MAS Studio",
  "kind": "beam", "schema": 1, "savedAt": "2026-10-19T08:00:00.000Z",
  "units": { "base": "SI-kN-m", "display": "kN-m" },
  "project": { "title": "Overhanging beam" },
  "model": {
    "L": 8, "material": "steel", "E": 200, "nu": 0.3, "rho": 7850, "selfWeight": false,
    "section": { "type": "I", "d": 300, "bf": 150, "tf": 10, "tw": 7 },
    "supports": [ { "type": "pin", "x": 0 }, { "type": "roller", "x": 6 } ],
    "loads": [
      { "type": "udl", "dir": "-y", "w": 12, "x1": 0, "x2": 6 },
      { "type": "point", "dir": "-y", "P": 20, "x": 8 }
    ]
  },
  "settings": { "momentDiagram": "up", "sectionCut": 6 }
}
```
