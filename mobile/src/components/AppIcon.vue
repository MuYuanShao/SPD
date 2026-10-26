<script setup lang="ts">
import { computed } from 'vue'
import { ShoppingCart, Package, ScanLine, UserRound, LockKeyhole, Eye, EyeOff, Settings, ChevronRight, ChevronLeft, CloudUpload, LogOut, Info, Camera, Check, ClipboardList, Wifi, ShieldCheck } from '@lucide/vue'
const props=withDefaults(defineProps<{name:string;size?:number}>(),{size:22})
const icons={cart:ShoppingCart,package:Package,scan:ScanLine,user:UserRound,lock:LockKeyhole,eye:Eye,'eye-off':EyeOff,settings:Settings,right:ChevronRight,back:ChevronLeft,cloud:CloudUpload,logout:LogOut,info:Info,camera:Camera,check:Check,list:ClipboardList,wifi:Wifi,shield:ShieldCheck}
const icon=computed(()=>icons[props.name as keyof typeof icons] || Info)
</script>
<template><component :is="icon" :size="size" :stroke-width="1.8" aria-hidden="true" /></template>
