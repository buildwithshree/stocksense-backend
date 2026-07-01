package com.stocksense.enums;

// ─── UserRole ────────────────────────────────────────────────────────────────
// Maps to PostgreSQL user_role enum. Must match exactly (case-sensitive).
// Spring Security uses this to gate @PreAuthorize("hasRole('ADMIN')") checks.
// Note: Spring Security prefixes roles with "ROLE_" internally — handled in
// UserDetailsImpl.getAuthorities() by returning SimpleGrantedAuthority("ROLE_" + role.name()).
