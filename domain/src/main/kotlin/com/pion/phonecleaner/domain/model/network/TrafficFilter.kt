package com.pion.phonecleaner.domain.model.network

/**
 * Which connection's bytes a row reports.
 *
 * `NetworkStatsManager` reports received and transmitted bytes separately, and this app sums them:
 * the split it offers the user is mobile-versus-Wi-Fi, never download-versus-upload
 * (`docs/reverse-engineering/19-network-and-speed-test.md` :511-517). Do not add a `Download`/`Upload`
 * constant here without also splitting the query — a filter the query cannot answer renders zeroes
 * that look measured.
 */
enum class TrafficFilter { All, Mobile, Wifi }
