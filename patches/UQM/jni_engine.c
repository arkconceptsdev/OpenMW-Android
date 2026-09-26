/* src/jni_engine.c – UPDATED FOR UQMKt */
#include <jni.h>
#include "uqm/globdata.h"
#include "uqm/sis.h"
#include "uqm/encount.h"
#include "uqm/units.h"

#define JNI_CLASS "org/openmw/utils/UQMKt"

/* ---------- Activity & Battle ---------- */
JNIEXPORT jint JNICALL
Java_org_openmw_utils_UQMKt_getActivity(JNIEnv *env, jclass clazz) {
    (void)env; (void)clazz;
    return (jint)GLOBAL(CurrentActivity);
}

JNIEXPORT jboolean JNICALL
Java_org_openmw_utils_UQMKt_isInBattle(JNIEnv *env, jclass clazz) {
    (void)env; (void)clazz;
    return (GLOBAL(CurrentActivity) & IN_BATTLE) != 0;
}

/* ---------- Ship screen position ---------- */
JNIEXPORT jint JNICALL
Java_org_openmw_utils_UQMKt_getShipScreenX(JNIEnv *env, jclass clazz) {
    return (jint)GLOBAL(ShipStamp.origin.x);
}

JNIEXPORT jint JNICALL
Java_org_openmw_utils_UQMKt_getShipScreenY(JNIEnv *env, jclass clazz) {
    return (jint)GLOBAL(ShipStamp.origin.y);
}

/* ---------- True universe position (log) ---------- */
JNIEXPORT jint JNICALL
Java_org_openmw_utils_UQMKt_getLogX(JNIEnv *env, jclass clazz) {
    return (jint)GLOBAL_SIS(log_x);
}

JNIEXPORT jint JNICALL
Java_org_openmw_utils_UQMKt_getLogY(JNIEnv *env, jclass clazz) {
    return (jint)GLOBAL_SIS(log_y);
}

/* ---------- Autopilot destination ---------- */
JNIEXPORT jint JNICALL
Java_org_openmw_utils_UQMKt_getAutopilotX(JNIEnv *env, jclass clazz) {
    return (jint)GLOBAL(autopilot.x);
}

JNIEXPORT jint JNICALL
Java_org_openmw_utils_UQMKt_getAutopilotY(JNIEnv *env, jclass clazz) {
    return (jint)GLOBAL(autopilot.y);
}

/* ---------- Inter-planetary cursor ---------- */
JNIEXPORT jint JNICALL
Java_org_openmw_utils_UQMKt_getIPLocationX(JNIEnv *env, jclass clazz) {
    return (jint)GLOBAL(ip_location.x);
}

JNIEXPORT jint JNICALL
Java_org_openmw_utils_UQMKt_getIPLocationY(JNIEnv *env, jclass clazz) {
    return (jint)GLOBAL(ip_location.y);
}

/* ---------- Resources ---------- */
JNIEXPORT jint JNICALL
Java_org_openmw_utils_UQMKt_getCrew(JNIEnv *env, jclass clazz) {
    return (jint)GLOBAL_SIS(CrewEnlisted);
}

JNIEXPORT jint JNICALL
Java_org_openmw_utils_UQMKt_getFuel(JNIEnv *env, jclass clazz) {
    return (jint)GLOBAL_SIS(FuelOnBoard);
}

JNIEXPORT jint JNICALL
Java_org_openmw_utils_UQMKt_getRU(JNIEnv *env, jclass clazz) {
    return (jint)GLOBAL_SIS(ResUnits);
}

/* ---------- Minerals ---------- */
JNIEXPORT void JNICALL
Java_org_openmw_utils_UQMKt_getMinerals(JNIEnv *env, jclass clazz, jintArray out) {
    jint tmp[8];
    for (int i = 0; i < 8; ++i)
        tmp[i] = (jint)GLOBAL_SIS(ElementAmounts[i]);
    (*env)->SetIntArrayRegion(env, out, 0, 8, tmp);
}

/* ---------- Drive Slots ---------- */
JNIEXPORT void JNICALL
Java_org_openmw_utils_UQMKt_getDriveSlots(JNIEnv *env, jclass clazz, jbyteArray out) {
    jbyte tmp[NUM_DRIVE_SLOTS];
    for (int i = 0; i < NUM_DRIVE_SLOTS; ++i)
        tmp[i] = (jbyte)GLOBAL_SIS(DriveSlots[i]);
    (*env)->SetByteArrayRegion(env, out, 0, NUM_DRIVE_SLOTS, tmp);
}

/* ---------- Jet Slots ---------- */
JNIEXPORT void JNICALL
Java_org_openmw_utils_UQMKt_getJetSlots(JNIEnv *env, jclass clazz, jbyteArray out) {
    jbyte tmp[NUM_JET_SLOTS];
    for (int i = 0; i < NUM_JET_SLOTS; ++i)
        tmp[i] = (jbyte)GLOBAL_SIS(JetSlots[i]);
    (*env)->SetByteArrayRegion(env, out, 0, NUM_JET_SLOTS, tmp);
}

/* ---------- Modules ---------- */
JNIEXPORT void JNICALL
Java_org_openmw_utils_UQMKt_getModules(JNIEnv *env, jclass clazz, jbyteArray out) {
    jbyte tmp[16];
    for (int i = 0; i < 16; ++i)
        tmp[i] = (jbyte)GLOBAL_SIS(ModuleSlots[i]);
    (*env)->SetByteArrayRegion(env, out, 0, 16, tmp);
}

/* ---------- Names ---------- */
JNIEXPORT jstring JNICALL
Java_org_openmw_utils_UQMKt_getShipName(JNIEnv *env, jclass clazz) {
    (void)clazz;
    return (*env)->NewStringUTF(env, GLOBAL_SIS(ShipName));
}

JNIEXPORT jstring JNICALL
Java_org_openmw_utils_UQMKt_getCommanderName(JNIEnv *env, jclass clazz) {
    (void)clazz;
    return (*env)->NewStringUTF(env, GLOBAL_SIS(CommanderName));
}
