import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import assert from 'node:assert/strict';

const nodes = new Map();
function element(selector) {
  if (!nodes.has(selector)) nodes.set(selector, {
    value: selector.includes('filter') ? 'ALL' : '', dataset: {}, hidden: false, disabled: false,
    style: { setProperty() {} }, events: {}, textContent: '', innerHTML: '',
    addEventListener(name, fn) { this.events[name] = fn; },
    setAttribute() {}, removeAttribute() {}, focus() {}, close() {},
    querySelector() { return element('#submit'); }
  });
  return nodes.get(selector);
}
const events = {};
const context = vm.createContext({
  console, Intl, Date, Number, String, Math, Error, encodeURIComponent,
  document: { querySelector: element },
  window: { matchMedia: () => ({matches:true}), addEventListener: (name, fn) => events[name] = fn, location: {reload() {}}, print() {} },
  fetch: async () => ({ok:false,status:401,text:async ()=>'{}'}),
  performance: {now:()=>0}, cancelAnimationFrame() {}, confirm:()=>true
});
vm.runInContext(readFileSync(new URL('../src/main/webapp/app.js', import.meta.url), 'utf8'), context);
await new Promise(resolve=>setImmediate(resolve));
const run = code => vm.runInContext(code, context);
assert.equal(run("shiftMonth('2026-12',1)"), '2027-01');
assert.equal(run("shiftMonth('2026-01',-1)"), '2025-12');
assert.equal(run("shiftMonth('2026-09',1)"), '2026-10');
console.log('PASS month boundaries');
context.sample = {month:'2026-09',profile:{name:'Audit',monthlyBudget:5000},income:1000,expenses:300,balance:700,spendingByCategory:{Food:300},
  transactions:Array.from({length:6},(_,i)=>({id:String(i+1),description:`Entry ${i} <script>`,category:'Food',type:'EXPENSE',amount:50,date:'2026-09-01'}))};
run('renderDashboard(sample)');
assert.equal((element('#transactions').innerHTML.match(/class="transaction"/g)||[]).length, 5);
assert.ok(element('#transactions').innerHTML.includes('&lt;script&gt;'));
events.beforeprint();
assert.equal((element('#transactions').innerHTML.match(/class="transaction"/g)||[]).length, 6);
events.afterprint();
assert.equal((element('#transactions').innerHTML.match(/class="transaction"/g)||[]).length, 5);
console.log('PASS pagination, escaping, complete print and restoration');
run('setBudgetEditing(true)'); element('#budget-input').value = 999;
run('setBudgetEditing(false,true)');
assert.equal(element('#budget-input').value,5000);
assert.equal(element('#budget-save').hidden,true);
console.log('PASS budget discard');
context.fetch = async()=>{throw new Error('offline');};
const button = element('#logout-button');
const event = {currentTarget:button};
const logout = button.events.click(event);
event.currentTarget = null; // Browsers clear this after dispatch, before await resumes.
await logout;
assert.equal(button.disabled,false);
console.log('PASS failed logout restores button without TypeError');
let resolveRequest;
context.fetch = ()=>new Promise(resolve=>resolveRequest=resolve);
const pending = run("requestJson('api?month=2026-09')");
const before = run('selectedMonth');
await run('moveMonth(1)');
assert.equal(run('selectedMonth'),before);
resolveRequest({ok:true,status:200,text:async()=>JSON.stringify(context.sample)});
await pending;
assert.equal(element('#dashboard-content').inert,false);
console.log('PASS concurrent navigation blocked during pending API request');
context.fetch = async()=>({ok:true,status:200,text:async()=>'<html>unexpected</html>'});
await assert.rejects(run("requestJson('api')"), /Unexpected server response/);
assert.equal(element('#dashboard-content').inert,false);
console.log('PASS malformed response reported and interaction restored');
