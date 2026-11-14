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


test('reconoce la tienda junto al distintivo Star Seller y conserva ambos datos', () => {
  const anchor={
    textContent:'Tokki Shop', innerText:'Tokki Shop',
    getAttribute:name=> name==='href' ? '/store.html?store_id=12345' : null
  };
  const storePanel={
    innerText:'Tokki Shop\nStar Seller',
    textContent:'Tokki Shop\nStar Seller',
    querySelectorAll:selector=>selector==='a[href]' ? [anchor] : [],
    parentElement:null
  };
  const badge={textContent:'Star Seller',children:[],parentElement:storePanel};
  const head={textContent:'Funda',parentElement:{innerText:'ARS 9.507',parentElement:null}};
  const document={
    querySelectorAll:selector=>selector.includes('span, strong') ? [badge] : [],
    querySelector:selector=>selector==='h1'?head:null
  };
  const context={
    window:{location:{href:'https://www.temu.com/ar-en/funda-g-606892013277333.html'}},
    URL, document
  };
  vm.runInNewContext(source+'\nthis.result = extractTemu();',context);
  const data=JSON.parse(JSON.stringify(context.result));
  assert.equal(data.seller,'Tokki Shop');
  assert.equal(data.sellerBadge,'TEMU_STAR_SELLER');
  assert.equal(data.currency,'ARS');
});

test('no convierte una etiqueta Star Seller sin tienda en una identidad', () => {
  const panel={
    innerText:'Star Seller',
    textContent:'Star Seller',
    querySelectorAll:()=>[],
    parentElement:null
  };
  const badge={textContent:'Star Seller',children:[],parentElement:panel};
  const document={
    querySelectorAll:selector=>selector.includes('span, strong') ? [badge] : [],
    querySelector:selector=>selector==='h1'?{textContent:'Funda',parentElement:null}:null
  };
  const context={
    window:{location:{href:'https://www.temu.com/ar-en/funda-g-606892013277333.html'}},
    URL, document
  };
  vm.runInNewContext(source+'\nthis.result = extractTemu();',context);
  const data=JSON.parse(JSON.stringify(context.result));
  assert.equal(data.seller,null);
  assert.equal(data.sellerBadge,null);
});

test('lee la identidad estructurada sin inventar el distintivo Star Seller', () => {
  const data=extract('https://www.temu.com/ar-en/funda-g-606892013277333.html',
    {'@type':'Product',offers:{
      price:'9507',priceCurrency:'ARS',
      seller:{'@type':'Organization',name:'Tokki Shop'}
    }});
  assert.equal(data.seller,'Tokki Shop');
  assert.equal(data.sellerBadge,null);
});


test('detecta tienda normal sin Star Seller mediante Sold by', () => {
  const sellerBlock={innerText:'Sold by Tokki Shop',textContent:'Sold by Tokki Shop'};
  const root={querySelectorAll:selector=>
    selector==='section, div, p, span' ? [sellerBlock] : []};
  const document={
    querySelectorAll:()=>[],
    querySelector:selector=> selector==='main'?root
      : selector==='h1'
        ? {textContent:'Funda',parentElement:{innerText:'ARS 9.507'}}
        : null
  };
  const context={
    window:{location:{href:'https://www.temu.com/ar-en/funda-g-606892013277333.html'}},
    URL,document
  };
  vm.runInNewContext(source+'\nthis.result = extractTemu();',context);
  const result=JSON.parse(JSON.stringify(context.result));
  assert.equal(result.seller,'Tokki Shop');
  assert.equal(result.sellerBadge,null);
});

test('no inventa vendedor si aparecen distintas tiendas en un sector ambiguo', () => {
  const blocks=['Sold by Tienda Uno','Sold by Tienda Dos'].map(innerText=>({innerText}));
  const root={querySelectorAll:selector=>
    selector==='section, div, p, span' ? blocks : []};
  const document={
    querySelectorAll:()=>[],
    querySelector:selector=> selector==='main'?root
      : selector==='h1'?{textContent:'Funda',parentElement:null}:null
  };
  const context={
    window:{location:{href:'https://www.temu.com/ar-en/funda-g-606892013277333.html'}},
    URL,document
  };
  vm.runInNewContext(source+'\nthis.result = extractTemu();',context);
  const result=JSON.parse(JSON.stringify(context.result));
  assert.equal(result.seller,null);
  assert.equal(result.sellerBadge,null);
});


test('la valoración del producto 4.7 no sustituye a Tokki Shop y su calificación 4.8', () => {
  const sellerPanel={
    innerText:'Tokki Shop\n4.8\nStar Seller\n4.7 out of five stars',
    textContent:'Tokki Shop\n4.8\nStar Seller\n4.7 out of five stars',
    parentElement:null,
    querySelectorAll:()=>[]
  };
  const badge={textContent:'Star Seller',children:[],parentElement:sellerPanel};
  const document={
    querySelectorAll:selector=>selector.startsWith('span, strong')?[badge]:[],
    querySelector:selector=>selector==='h1'
      ? {textContent:'Funda de teléfono',parentElement:{innerText:'ARS 9.507'}} : null
  };
  const context={
    window:{location:{href:'https://www.temu.com/ar-en/funda-g-606892013277333.html'}},
    URL,document
  };
  vm.runInNewContext(source+'\nthis.result = extractTemu();',context);
  const data=JSON.parse(JSON.stringify(context.result));
  assert.equal(data.seller,'Tokki Shop');
  assert.equal(data.temuSellerRating,4.8);
  assert.equal(data.sellerBadge,'TEMU_STAR_SELLER');
});

test('una frase 4.7 out of five stars no es vendedor ni calificación de tienda', () => {
  const sellerPanel={
    innerText:'4.7 out of five stars\nStar Seller',
    textContent:'4.7 out of five stars\nStar Seller',
    parentElement:null,
    querySelectorAll:()=>[]
  };
  const badge={textContent:'Star Seller',children:[],parentElement:sellerPanel};
  const document={
    querySelectorAll:selector=>selector.startsWith('span, strong')?[badge]:[],
    querySelector:selector=>selector==='h1'
      ? {textContent:'Funda de teléfono',parentElement:null}:null
  };
  const context={
    window:{location:{href:'https://www.temu.com/ar-en/funda-g-606892013277333.html'}},
    URL,document
  };
  vm.runInNewContext(source+'\nthis.result = extractTemu();',context);
  const data=JSON.parse(JSON.stringify(context.result));
  assert.equal(data.seller,null);
  assert.equal(data.temuSellerRating,null);
  assert.equal(data.sellerBadge,null);
});

test('una tienda normal con ★4.8 puede tener rating sin sello Star Seller', () => {
  const block={innerText:'Sold by Tokki Shop\n★ 4.8',textContent:'Sold by Tokki Shop\n★ 4.8',parentElement:null};
  const root={querySelectorAll:selector=>selector==='section, div, p, span'?[block]:selector.includes('a[href]')?[block]:[]};
  const document={
    querySelectorAll:()=>[],
    querySelector:selector=>selector==='main'?root:selector==='h1'
      ? {textContent:'Funda',parentElement:null}:null
  };
  const context={
    window:{location:{href:'https://www.temu.com/ar-en/funda-g-606892013277333.html'}},
    URL,document
  };
  vm.runInNewContext(source+'\nthis.result = extractTemu();',context);
  const data=JSON.parse(JSON.stringify(context.result));
  assert.equal(data.seller,'Tokki Shop');
  assert.equal(data.temuSellerRating,4.8);
  assert.equal(data.sellerBadge,null);
});


test('captura ID mall_id y recuento exacto de reseñas de TIENDA', () => {
  const link={
    textContent:'Tokki Shop',
    innerText:'Tokki Shop',
    getAttribute:key=>key==='href'?'/mall.html?mall_id=613456789012345':null
  };
  const panel={
    innerText:'Tokki Shop\n4.8\n1.234 store reviews\nStar Seller',
    textContent:'Tokki Shop\n4.8\n1.234 store reviews\nStar Seller',
    parentElement:null,
    querySelectorAll:sel=>sel==='a[href]'?[link]:[]
  };
  const badge={textContent:'Star Seller',children:[],parentElement:panel};
  const document={
    querySelectorAll:selector=>selector.startsWith('span, strong')?[badge]:[],
    querySelector:selector=>selector==='h1'
      ? {textContent:'Funda',parentElement:{innerText:'ARS 9.507'}}:null
  };
  const context={
    window:{location:{href:'https://www.temu.com/ar-en/funda-g-606892013277333.html'}},
    URL,document
  };
  vm.runInNewContext(source+'\nthis.result = extractTemu();',context);
  const observed=JSON.parse(JSON.stringify(context.result));
  assert.equal(observed.seller,'Tokki Shop');
  assert.equal(observed.temuStoreId,'613456789012345');
  assert.equal(observed.sellerProfileUrl,'https://www.temu.com/mall.html?mall_id=613456789012345');
  assert.equal(observed.temuSellerReviewCount,1234);
  assert.equal(observed.temuSellerSectionObserved,true);
});

test('no usa las reseñas del PRODUCTO como recuento de la tienda', () => {
  const panel={
    innerText:'Tokki Shop\n4.8\n1,234 product reviews\nStar Seller',
    textContent:'Tokki Shop\n4.8\n1,234 product reviews\nStar Seller',
    parentElement:null,querySelectorAll:()=>[]
  };
  const badge={textContent:'Star Seller',children:[],parentElement:panel};
  const document={
    querySelectorAll:selector=>selector.startsWith('span, strong')?[badge]:[],
    querySelector:selector=>selector==='h1'?{textContent:'Funda',parentElement:null}:null
  };
  const context={
    window:{location:{href:'https://www.temu.com/ar-en/funda-g-606892013277333.html'}},
    URL,document
  };
  vm.runInNewContext(source+'\nthis.result = extractTemu();',context);
  const data=JSON.parse(JSON.stringify(context.result));
  assert.equal(data.seller,'Tokki Shop');
  assert.equal(data.temuSellerReviewCount,null);
  assert.equal(data.temuSellerRating,4.8);
});

test('tienda inspeccionada sin puntuación: reputación desconocida, no 0 ventas', () => {
  const panel={innerText:'Sold by Tokki Shop',textContent:'Sold by Tokki Shop',parentElement:null};
  const root={querySelectorAll:selector=>selector==='section, div, p, span'?[panel]:[]};
  const document={
    querySelectorAll:()=>[],
    querySelector:selector=>selector==='main'?root:
      selector==='h1'?{textContent:'Funda',parentElement:null}:null
  };
  const context={
    window:{location:{href:'https://www.temu.com/ar-en/funda-g-606892013277333.html'}},
    URL,document
  };
  vm.runInNewContext(source+'\nthis.result = extractTemu();',context);
  const data=JSON.parse(JSON.stringify(context.result));
  assert.equal(data.seller,'Tokki Shop');
  assert.equal(data.temuSellerRating,null);
  assert.equal(data.temuSellerReviewCount,null);
  assert.equal(data.temuSellerSectionObserved,true);
});


test('extrae los atributos y la aclaración de goodsDetail sin tomar imágenes ni Item ID', () => {
  const field = (label, value) => ({
    querySelector: selector => selector === '._5pgrvPWK'
      ? {textContent:label} : selector === '._2N4zowM4'
        ? {textContent:value}:null
  });
  const specification = (label, value) => ({
    querySelector: selector => selector === '._3c5b9cNv'
      ? {firstChild:{textContent:label + ':'}} :
      selector === '._2bgv8UEs > span' ? {textContent:value}:null
  });
  const details = {
    querySelectorAll: selector => selector === '._3zjMlOk4'
      ? [field('Feature', 'Air Cushion, Wireless Charging, Compatible With Magsafe')]
      : selector === '._1YBVObhm'
        ? [specification('Main Material', 'EVA'),
          specification('Item ID', '35M226G90Q')]
        : [],
    querySelector: selector => selector === '._2lHZTmSd'
      ? {textContent:'The packaging box shown in the image is for display purposes only and does not come with the product.'}
      : null
  };
  const document = {
    querySelectorAll:()=>[],
    querySelector: selector => selector === '#goodsDetail' ? details
      : selector === 'h1' ? {textContent:'Funda magnética',parentElement:null}:null
  };
  const context = {
    window:{location:{href:'https://www.temu.com/ar-en/funda-g-606892013277333.html'}},
    URL,document
  };
  vm.runInNewContext(source+'\nthis.result = extractTemu();',context);
  const data=JSON.parse(JSON.stringify(context.result));
  assert.match(data.description,/Feature: Air Cushion, Wireless Charging/);
  assert.match(data.description,/Main Material: EVA/);
  assert.match(data.description,/packaging box shown/);
  assert.doesNotMatch(data.description,/35M226G90Q/);
  assert.doesNotMatch(data.description,/Item ID/);
});

test('mantiene la descripción JSON-LD si goodsDetail no está presente', () => {
  const data=extract('https://www.temu.com/ar-en/funda-g-606892013277333.html',
    {'@type':'Product',description:'Funda compatible con carga inalámbrica',
      offers:{price:'9507',priceCurrency:'ARS'}});
  assert.equal(data.description,'Funda compatible con carga inalámbrica');
});


test('separa 4.7 y 286 reseñas del producto de 4.8 de la tienda en JSON-LD', () => {
  const data=extract('https://www.temu.com/ar-en/funda-g-606892013277333.html',
    {'@type':'Product',aggregateRating:{'@type':'AggregateRating',
      ratingValue:'4.7',reviewCount:'286'},offers:{
        price:'9507',priceCurrency:'ARS',seller:{name:'Tokki Shop'}}});
  assert.equal(data.temuProductRating,4.7);
  assert.equal(data.temuProductReviewCount,286);
  assert.equal(data.temuSellerRating,null);
  assert.equal(data.seller,'Tokki Shop');
});

test('extrae la pareja cercana al título con etiquetas visibles de producto', () => {
  const header={innerText:'4.7 out of five stars\n286 reviews',parentElement:null,
    querySelectorAll:()=>[]};
  const document={
    querySelectorAll:()=>[],
    querySelector:selector=>selector==='h1'
      ? {textContent:'Funda',parentElement:header}:null
  };
  const context={
    window:{location:{href:'https://www.temu.com/ar-en/funda-g-606892013277333.html'}},
    URL,document
  };
  vm.runInNewContext(source+'\nthis.result = extractTemu();',context);
  const data=JSON.parse(JSON.stringify(context.result));
  assert.equal(data.temuProductRating,4.7);
  assert.equal(data.temuProductReviewCount,286);
  assert.equal(data.temuSellerRating,null);
});

test('no usa ratingCount como cantidad de reseñas escritas del producto', () => {
  const data=extract('https://www.temu.com/ar-en/funda-g-606892013277333.html',
    {'@type':'Product',aggregateRating:{ratingValue:'4.7',ratingCount:'286'}});
  assert.equal(data.temuProductRating,null);
  assert.equal(data.temuProductReviewCount,null);
});

test('rechaza reseñas abreviadas sin recuento exacto', () => {
  const header={innerText:'4.7 out of five stars\n1.2K+ reviews',parentElement:null,
    querySelectorAll:()=>[]};
  const document={
    querySelectorAll:()=>[],
    querySelector:selector=>selector==='h1'
      ? {textContent:'Funda',parentElement:header}:null
  };
  const context={
    window:{location:{href:'https://www.temu.com/ar-en/funda-g-606892013277333.html'}},
    URL,document
  };
  vm.runInNewContext(source+'\nthis.result = extractTemu();',context);
  const data=JSON.parse(JSON.stringify(context.result));
  assert.equal(data.temuProductRating,null);
  assert.equal(data.temuProductReviewCount,null);
});

test('no toma reseñas de tienda si faltan opiniones identificadas del producto', () => {
  const sellerPanel={
    innerText:'Tokki Shop\n4.8\n286 store reviews\nStar Seller',
    textContent:'Tokki Shop\n4.8\n286 store reviews\nStar Seller',
    parentElement:null,querySelectorAll:()=>[]
  };
  const badge={textContent:'Star Seller',children:[],parentElement:sellerPanel};
  const document={
    querySelectorAll:selector=>selector.startsWith('span, strong')?[badge]:[],
    querySelector:selector=>selector==='h1'
      ? {textContent:'Funda',parentElement:null}:null
  };
  const context={
    window:{location:{href:'https://www.temu.com/ar-en/funda-g-606892013277333.html'}},
    URL,document
  };
  vm.runInNewContext(source+'\nthis.result = extractTemu();',context);
  const data=JSON.parse(JSON.stringify(context.result));
  assert.equal(data.temuProductRating,null);
  assert.equal(data.temuProductReviewCount,null);
  assert.equal(data.temuSellerReviewCount,286);
});
