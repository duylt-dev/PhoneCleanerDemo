package com.pion.phonecleaner.data.photo

import androidx.exifinterface.media.ExifInterface

/**
 * The GPS tags a location strip clears.
 *
 * **One `Set`.** The competitor's `nd/e.b()` lists 32 entries, one of them — `GPSDestBearingRef` —
 * twice (`docs/reverse-engineering/13-photo-and-media.md` §4.4); a `Set` cannot contain a duplicate,
 * which is the whole reason §5.5 asks for this shape. The membership is otherwise that list exactly:
 * nothing is added to it here, because a tag nobody verified is a tag nobody checked.
 */
internal val GPS_TAGS: Set<String> = setOf(
    ExifInterface.TAG_GPS_LATITUDE,
    ExifInterface.TAG_GPS_LATITUDE_REF,
    ExifInterface.TAG_GPS_LONGITUDE,
    ExifInterface.TAG_GPS_LONGITUDE_REF,
    ExifInterface.TAG_GPS_ALTITUDE,
    ExifInterface.TAG_GPS_ALTITUDE_REF,
    ExifInterface.TAG_GPS_DATESTAMP,
    ExifInterface.TAG_GPS_PROCESSING_METHOD,
    ExifInterface.TAG_GPS_TIMESTAMP,
    ExifInterface.TAG_GPS_AREA_INFORMATION,
    ExifInterface.TAG_GPS_DEST_BEARING,
    ExifInterface.TAG_GPS_DEST_BEARING_REF,
    ExifInterface.TAG_GPS_DEST_DISTANCE,
    ExifInterface.TAG_GPS_DEST_LATITUDE,
    ExifInterface.TAG_GPS_DEST_LATITUDE_REF,
    ExifInterface.TAG_GPS_DEST_LONGITUDE,
    ExifInterface.TAG_GPS_DEST_LONGITUDE_REF,
    ExifInterface.TAG_GPS_DIFFERENTIAL,
    ExifInterface.TAG_GPS_DOP,
    ExifInterface.TAG_GPS_IMG_DIRECTION,
    ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
    ExifInterface.TAG_GPS_MAP_DATUM,
    ExifInterface.TAG_GPS_MEASURE_MODE,
    ExifInterface.TAG_GPS_SATELLITES,
    ExifInterface.TAG_GPS_SPEED,
    ExifInterface.TAG_GPS_SPEED_REF,
    ExifInterface.TAG_GPS_STATUS,
    ExifInterface.TAG_GPS_TRACK,
    ExifInterface.TAG_GPS_TRACK_REF,
    ExifInterface.TAG_GPS_VERSION_ID,
)

/**
 * The four tags whose presence *is* "this photo carries a location", per `nd/e.f(path)`
 * (`docs/reverse-engineering/13-photo-and-media.md` §4.4). Reading four attributes is what makes the
 * scan bounded; the strip then clears all thirty.
 */
internal val GPS_PRESENCE_TAGS: List<String> = listOf(
    ExifInterface.TAG_GPS_LATITUDE,
    ExifInterface.TAG_GPS_LATITUDE_REF,
    ExifInterface.TAG_GPS_LONGITUDE,
    ExifInterface.TAG_GPS_LONGITUDE_REF,
)
