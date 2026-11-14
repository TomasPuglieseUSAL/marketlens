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


test('muestra varios precios ARS del producto sin elegir promoción o referencia', () => {
  const priceBlock = {innerText: 'ARS 8.617 after applying promos to ARS9.507 ARS16.486'};
  const heading = {textContent:'Funda para teléfono', parentElement:priceBlock};
  const document={
    querySelectorAll:()=>[],
    querySelector:selector=>selector==='h1'?heading:null
  };
  const context={window:{location:{href:'https://www.temu.com/ar-en/case-g-606892013277333.html'}},URL,document};
  vm.runInNewContext(source+'\nthis.result = extractTemu();',context);
  const data=JSON.parse(JSON.stringify(context.result));
  assert.equal(data.currency,'ARS');
  assert.equal(data.price,null);
  assert.deepEqual(data.priceCandidates,['8.617','9.507','16.486']);
});
test('extrae un único importe ARS sin metadatos cuando el producto es inequívoco', () => {
  const priceBlock = {innerText:'ARS 9.507'};
  const document={
    querySelectorAll:()=>[],
    querySelector:selector=>selector==='h1'
      ? {textContent:'Funda para teléfono',parentElement:priceBlock}:null
  };
  const context={window:{location:{href:'https://www.temu.com/ar-en/case-g-606892013277333.html'}},URL,document};
  vm.runInNewContext(source+'\nthis.result = extractTemu();',context);
  const data=JSON.parse(JSON.stringify(context.result));
  assert.equal(data.currency,'ARS');
  assert.equal(data.price,'9.507');
});
