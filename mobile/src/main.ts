import { createApp } from 'vue'
import App from './App.vue'
import './style.css'
async function start() {
  if (import.meta.env.DEV && new URLSearchParams(location.search).get('demo') === '1') {
    const { enableDemo } = await import('./demoAdapter')
    enableDemo()
  }
  createApp(App).mount('#app')
}
void start()
