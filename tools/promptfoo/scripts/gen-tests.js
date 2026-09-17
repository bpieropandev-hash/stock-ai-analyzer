// Gera tests.yaml a partir de data/baseline_manifest.json.
// Reroda sempre que o baseline for recapturado (nova rodada de auditoria real).
const fs = require("fs");
const path = require("path");

const manifest = JSON.parse(
  fs.readFileSync(path.join(__dirname, "..", "data", "baseline_manifest.json"), "utf8")
);

function cap(s) {
  return s.charAt(0).toUpperCase() + s.slice(1);
}

const lines = [];
for (const row of manifest) {
  lines.push(`- description: "${row.ticker} regression vs baseline 2026-08-08"`);
  lines.push(`  vars:`);
  lines.push(`    prompt: file://${row.promptFile.replace(/\\/g, "/")}`);
  lines.push(`    ticker: ${row.ticker}`);
  for (const [dim, value] of Object.entries(row.baseline)) {
    const key = dim === "scoreGeral" ? "baselineScoreGeral" : `baseline${cap(dim)}`;
    lines.push(`    ${key}: ${value}`);
  }
  lines.push(`  assert:`);
  lines.push(`    - type: is-json`);
  lines.push(`      value: file://asserts/schema.json`);
  lines.push(`    - type: javascript`);
  lines.push(`      value: file://asserts/score-drift.js`);
  lines.push(`    - type: llm-rubric`);
  lines.push(`      value: file://asserts/coherence-rubric.txt`);
  lines.push(``);
}

const header = `# GERADO por scripts/gen-tests.js a partir de data/baseline_manifest.json — não editar à mão.\n# Rode "npm run gen-tests" depois de recapturar o baseline.\n\n`;
fs.writeFileSync(path.join(__dirname, "..", "tests.yaml"), header + lines.join("\n"), "utf8");
console.log(`tests.yaml gerado com ${manifest.length} casos.`);
