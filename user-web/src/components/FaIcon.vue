<script setup>
import { computed } from 'vue';
const props = defineProps({ name: String });
const codes = {
  home: 0xf015,
  cart: 0xf23d,
  compass: 0xf14e,
  user: 0xf007,
  envelope: 0xf003,
  back: 0xf104,
  arrow: 0xf105,
  down: 0xf107,
  phone: 0xf095,
  lock: 0xf023,
  userOutline: 0xf2c0,
  userPlus: 0xf234,
  bed: 0xf236,
  note: 0xf249,
  logout: 0xf08b,
  service: 0xf2a0,
  comment: 0xf0e5,
  users: 0xf0c0,
  edit: 0xf044,
  shield: 0xf132,
};
const glyph = computed(() => String.fromCharCode(codes[props.name] || codes.user));
</script>
<template>
  <i class="prototype-icon" aria-hidden="true">{{ glyph }}</i>
</template>
<style>
@font-face {
  font-family: PrototypeFA;
  src: url('../assets/fontawesome-webfont.woff2') format('woff2');
  font-weight: normal;
  font-style: normal;
  font-display: block;
}
.prototype-icon {
  font-family: PrototypeFA !important;
  font-weight: normal;
  font-style: normal;
  display: inline-block;
  line-height: 1;
  flex-shrink: 0;
}
</style>
