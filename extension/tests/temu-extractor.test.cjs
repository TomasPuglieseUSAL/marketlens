const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const source=fs.readFileSync(path.join(__dirname,'../popup/temu-extractor.js'),'utf8');

function extract(url, product) {
  const scripts=product?[{textContent:JSON.stringify(product)}]:[];
  const document={querySelectorAll:()=>scripts,querySelector:s=>s==='h1'?{textContent:'Producto de prueba'}:null};
  const context={window:{location:{href:url}},URL,document};
  vm.runInNewContext(source+'\nthis.result = extractTemu();',context);
  return JSON.parse(JSON.stringify(context.result));
}
test('extrae goods_id y precio ARS explícito',()=>{
  const x=extract('https://www.temu.com/goods.html?goods_id=601099512345678&tracking=other',
    {'@type':'Product',offers:{price:'1200',priceCurrency:'ARS'}});
  assert.equal(x.externalId,'601099512345678');
  assert.equal(x.price,'1200');
  assert.equal(x.seller,null);
  assert.equal(x.url,'https://www.temu.com/goods.html?goods_id=601099512345678');
});
test('ignora precios USD o intervalos',()=>{
  const url='https://www.temu.com/ejemplo-g-601099512345678.html';
  assert.equal(extract(url,{'@type':'Product',offers:{price:'10',priceCurrency:'USD'}}).price,null);
  assert.equal(extract(url,{'@type':'Product',offers:{lowPrice:1,highPrice:9,price:'3',priceCurrency:'ARS'}}).price,null);
});
test('rechaza búsqueda y dominios ajenos',()=>{
  assert.equal(extract('https://www.temu.com/search_result.html').found,false);
  assert.equal(extract('https://temu.com.evil.example/goods.html?goods_id=601099512345678').found,false);
});
