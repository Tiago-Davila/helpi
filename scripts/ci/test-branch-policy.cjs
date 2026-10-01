// Execute the actual trusted workflow policy against representative event metadata.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const workflow = fs.readFileSync('.github/workflows/branch-policy.yml', 'utf8');
const source = workflow.split('script: |\n')[1].split('\n').map(line => line.slice(12)).join('\n');
assert(source.includes('core.setFailed'));
for (const [base, head, repo, allowed] of [
  ['develop', 'feature/test', 'Tiago-Davila/helpi', true],
  ['main', 'develop', 'Tiago-Davila/helpi', true],
  ['main', 'feature/test', 'Tiago-Davila/helpi', false],
  ['main', 'develop', 'attacker/helpi', false],
  ['main', 'develop', null, false],
  ['develop', 'main', 'Tiago-Davila/helpi', true],
]) {
  let failed = false;
  vm.runInNewContext(source, {
    context: {payload: {
      repository: {full_name: 'Tiago-Davila/helpi'},
      pull_request: {base: {ref: base}, head: {ref: head, repo: repo ? {full_name: repo} : null}},
    }},
    core: {setFailed: () => { failed = true; }},
  });
  assert.equal(!failed, allowed, `${repo}:${head} -> ${base}`);
}
console.log('6 branch-policy scenarios passed');
