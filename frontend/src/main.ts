import {createApp} from 'vue'
import {createRouter,createWebHashHistory} from 'vue-router'
import App from './App.vue'
import Auth from './views/Auth.vue'
import Dashboard from './views/Dashboard.vue'
import Orders from './views/Orders.vue'
import OrderDetail from './views/OrderDetail.vue'
import Records from './views/Records.vue'
import Reports from './views/Reports.vue'
import Account from './views/Account.vue'
import {api,session} from './api'
import './style.css'
const router=createRouter({history:createWebHashHistory(),routes:[
 {path:'/login',component:Auth},{path:'/register',component:Auth},{path:'/forgot',component:Auth},{path:'/reset',component:Auth},
 {path:'/',component:Dashboard},{path:'/orders',component:Orders},{path:'/orders/:id',component:OrderDetail},
 ...['customers','vehicles','employees','inventory','audit'].map(p=>({path:'/'+p,component:Records})),
 {path:'/reports',component:Reports},{path:'/account',component:Account},{path:'/:pathMatch(.*)*',redirect:'/'}
]})
let loaded=false
router.beforeEach(async to=>{if(!loaded){try{session.user=await api('/auth/me')}catch{}loaded=true;session.loading=false}
 const publicRoute=['/login','/register','/forgot','/reset'].includes(to.path)
 if(!publicRoute&&!session.user)return '/login'
 if(session.user&&to.path==='/login')return '/'
 if(session.user?.role!=='ADMIN'&&['/customers','/employees','/reports','/audit'].includes(to.path))return '/'
 if(session.user?.role==='CLIENT'&&to.path==='/inventory')return '/'
})
createApp(App).use(router).mount('#app')
