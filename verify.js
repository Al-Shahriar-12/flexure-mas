/*
 * Flexure MAS solver check.
 * Runs the solver from index.html against closed-form textbook results.
 * Usage (Node.js 18 or newer):  node tests/verify.js
 */
"use strict";
const fs = require("fs");
const path = require("path");

const html = fs.readFileSync(path.join(__dirname, "..", "index.html"), "utf8");
const start = html.indexOf("/* ================= Structural solver");
const end = html.indexOf("/* ================= Helpers");
if (start < 0 || end < 0) { console.error("Solver section not found in index.html"); process.exit(1); }
const { analyze, probeAt } = new Function(html.slice(start, end) + "\nreturn { analyze, probeAt };")();

const rect = { type: "rect", b: 200, h: 400 };
const beam = (L, sups, loads) => ({ L, E: 200, nu: 0.3, rho: 7850, section: rect,
  supports: sups.map(([type, x]) => ({ type, x })), loads });
let pass = 0, fail = 0;
function check(name, got, expected) {
  const ok = Math.abs(got - expected) <= 1e-5 * Math.abs(expected) + 1e-9;   // 0.001 % or better
  if (ok) pass++; else fail++;
  console.log(`${ok ? "PASS" : "FAIL"}  ${name}: ${got.toPrecision(8)} (expected ${expected.toPrecision(8)})`);
}

// Simply supported beam, UDL w = 10 kN/m, L = 6 m
let r = analyze(beam(6, [["pin", 0], ["roller", 6]], [{ type: "udl", dir: "-y", w: 10, x1: 0, x2: 6 }]));
check("SS UDL: reaction wL/2", r.reactions[0].Ry, 30);
check("SS UDL: midspan moment wL²/8", probeAt(r, 3).Mz, 45);
check("SS UDL: midspan deflection 5wL⁴/384EI", probeAt(r, 3).v, -5 * 10 * 6 ** 4 / (384 * r.EIz));
check("SS UDL: end slope wL³/24EI", probeAt(r, 0).thz, -10 * 6 ** 3 / (24 * r.EIz));

// Cantilever, tip load P = 15 kN, L = 4 m
r = analyze(beam(4, [["fixed", 0]], [{ type: "point", dir: "-y", P: 15, x: 4 }]));
check("Cantilever: fixed-end moment PL", probeAt(r, 0).Mz, -60);
check("Cantilever: tip deflection PL³/3EI", probeAt(r, 4).v, -15 * 64 / (3 * r.EIz));

// Propped cantilever, UDL: prop reaction 3wL/8, fixed-end moment wL²/8
r = analyze(beam(6, [["fixed", 0], ["roller", 6]], [{ type: "udl", dir: "-y", w: 10, x1: 0, x2: 6 }]));
check("Propped UDL: prop reaction 3wL/8", r.reactions[1].Ry, 22.5);
check("Propped UDL: fixed-end moment wL²/8", probeAt(r, 0).Mz, -45);

// Propped cantilever, triangular load 0 → w at the prop: prop reaction 11wL/40
r = analyze(beam(6, [["fixed", 0], ["roller", 6]], [{ type: "uvl", dir: "-y", w1: 0, w2: 20, x1: 0, x2: 6 }]));
check("Propped UVL: prop reaction 11wL/40", r.reactions[1].Ry, 11 * 20 * 6 / 40);

// Fixed-fixed beam, UDL: end moments wL²/12, midspan wL²/24, deflection wL⁴/384EI
r = analyze(beam(6, [["fixed", 0], ["fixed", 6]], [{ type: "udl", dir: "-y", w: 10, x1: 0, x2: 6 }]));
check("Fixed-fixed UDL: end moment wL²/12", probeAt(r, 0).Mz, -30);
check("Fixed-fixed UDL: midspan moment wL²/24", probeAt(r, 3).Mz, 15);
check("Fixed-fixed UDL: midspan deflection wL⁴/384EI", probeAt(r, 3).v, -10 * 6 ** 4 / (384 * r.EIz));

// Two equal spans, UDL: middle reaction 1.25wL, support moment wL²/8
r = analyze(beam(10, [["pin", 0], ["roller", 5], ["roller", 10]], [{ type: "udl", dir: "-y", w: 10, x1: 0, x2: 10 }]));
check("Two spans UDL: middle reaction 1.25wL", r.reactions[1].Ry, 62.5);
check("Two spans UDL: support moment wL²/8", probeAt(r, 5).Mz, -31.25);

// Internal hinge (Gerber beam): fixed at 0, hinge at 5, roller at 12, UDL 8 kN/m, P = 25 kN at 9
r = analyze(beam(12, [["fixed", 0], ["hinge", 5], ["roller", 12]], [{ type: "udl", dir: "-y", w: 8, x1: 0, x2: 12 }, { type: "point", dir: "-y", P: 25, x: 9 }]));
check("Hinged beam: roller reaction by statics", r.reactions.find(q => q.x === 12).Ry, (8 * 7 * 3.5 + 25 * 4) / 7);
check("Hinged beam: zero moment at the hinge", probeAt(r, 5).Mz, 0);

// Axial: fixed-free bar, tip load P: N = P, u = PL/EA
r = analyze(beam(4, [["fixed", 0]], [{ type: "point", dir: "+x", P: 100, x: 4 }]));
check("Axial bar: tension N = P", probeAt(r, 2).N, 100);
check("Axial bar: tip extension PL/EA", probeAt(r, 4).u, 100 * 4 / r.EA);

// Axial: fixed-fixed bar, P at a: N = Pb/L left of the load, −Pa/L right of it
r = analyze(beam(6, [["fixed", 0], ["fixed", 6]], [{ type: "point", dir: "+x", P: 60, x: 2 }]));
check("Axial fixed-fixed: N left = Pb/L", probeAt(r, 1).N, 40);
check("Axial fixed-fixed: N right = −Pa/L", probeAt(r, 4).N, -20);

// Axial: fixed-free bar, uniform axial load p: N(0) = pL, u(L) = pL²/2EA
r = analyze(beam(5, [["fixed", 0]], [{ type: "udl", dir: "+x", w: 8, x1: 0, x2: 5 }]));
check("Distributed axial: N(0) = pL", probeAt(r, 0).N, 40);
check("Distributed axial: u(L) = pL²/2EA", probeAt(r, 5).u, 8 * 25 / (2 * r.EA));

// Stability: one roller alone must be reported as a mechanism
r = analyze(beam(6, [["roller", 0]], [{ type: "udl", dir: "-y", w: 10, x1: 0, x2: 6 }]));
if (!r.ok && r.unstable) { pass++; console.log("PASS  Unstable beam is reported as a mechanism"); }
else { fail++; console.log("FAIL  Unstable beam was not reported"); }

console.log(`\n${pass} passed, ${fail} failed`);
process.exit(fail ? 1 : 0);
