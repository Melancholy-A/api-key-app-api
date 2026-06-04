const fs = require('fs');
const path = require('path');
const vm = require('vm');

const root = path.resolve(__dirname, '..');
const chatHtml = fs.readFileSync(path.join(root, 'app/src/main/assets/chat.html'), 'utf8');
const katex = require(path.join(root, 'app/src/main/assets/vendor/katex/katex.min.js'));

function extractFunctionSource(name) {
  const marker = 'function ' + name + '(';
  const start = chatHtml.indexOf(marker);
  if (start < 0) {
    throw new Error('Missing function ' + name);
  }
  const braceStart = chatHtml.indexOf('{', start);
  let depth = 0;
  for (let index = braceStart; index < chatHtml.length; index += 1) {
    const ch = chatHtml[index];
    if (ch === '{') depth += 1;
    if (ch === '}') {
      depth -= 1;
      if (depth === 0) {
        return chatHtml.slice(start, index + 1);
      }
    }
  }
  throw new Error('Unterminated function ' + name);
}

const context = {};
vm.createContext(context);
for (const name of ['repairLatexControlWordSpacing', 'normalizeLatexExpression']) {
  if (chatHtml.includes('function ' + name + '(')) {
    vm.runInContext(extractFunctionSource(name), context);
  }
}

function normalize(tex) {
  return vm.runInContext('normalizeLatexExpression(' + JSON.stringify(tex) + ')', context);
}

function assertParseable(original, expected) {
  const normalized = normalize(original);
  if (normalized !== expected) {
    throw new Error('Expected ' + JSON.stringify(expected) + ' but got ' + JSON.stringify(normalized));
  }
  katex.__parse(normalized, { throwOnError: true, strict: false });
}

assertParseable('\\Deltat_{A,min}/t_A', '\\Delta t_{A,min}/t_A');
assertParseable('\\DeltaD_{min}/(0.434G\\mut_A)', '\\Delta D_{min}/(0.434G\\mu t_A)');
assertParseable('\\sigma_x+\\thetaA', '\\sigma_x+\\theta A');
assertParseable('\\Deltat', '\\Delta t');

console.log('chat math normalization ok');
