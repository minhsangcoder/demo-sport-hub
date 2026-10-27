export async function installPeerContentTiming(context,name) {
  await context.addInitScript(({name})=>{
    const labels={search:'Sân kiểm thử main',schedule:'Còn trống',booking:'Lượt đặt đã chuyển nhượng một lần, không thể đăng bán tiếp.',report:'Chuyển nhượng trực tiếp B → A'}
    window.__peerContentReady=0
    let scheduled=false
    function visible(node) {return node&&node.getClientRects().length>0&&getComputedStyle(node).visibility!=='hidden'}
    function ready() {
      if(!document.body)return false
      if(name==='bank')return [...document.querySelectorAll('main label')].some(label=>label.textContent.trim().startsWith('Chọn tài khoản đã khai')&&visible(label.querySelector('select')))
      const nodes=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT)
      while(nodes.nextNode()) {
        const node=nodes.currentNode,parent=node.parentElement
        if(node.nodeValue.trim()===labels[name]&&!parent?.closest('script,style,template')&&visible(parent))return true
      }
      return false
    }
    const observer=new MutationObserver(check)
    function check() {
      if(window.__peerContentReady||scheduled||!ready())return
      scheduled=true
      requestAnimationFrame(()=>{
        scheduled=false
        if(ready()) {window.__peerContentReady=performance.now();observer.disconnect()}
      })
    }
    observer.observe(document,{childList:true,subtree:true,characterData:true,attributes:true})
    document.addEventListener('DOMContentLoaded',check,{once:true})
    window.addEventListener('load',check,{once:true})
    check()
  },{name})
}
