package com.example.jianxiongrao.gpshook;

import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Created by Jianxiong Rao on 2017/10/13.
 */

public class GPSHook implements IXposedHookLoadPackage{
    private final String TAG = "Xposed";
    private XC_LoadPackage.LoadPackageParam mLpp;

    //不带参数的拦截方法
    private void hook_method(String className,ClassLoader classLoader, String methodName,
                             Object... parameterTypesAndCallback){
        try {
            XposedHelpers.findAndHookMethod(className, classLoader, methodName, parameterTypesAndCallback);
        } catch (Exception e) {
            XposedBridge.log(e);
        }
    }
    //带参数的方法拦截
    private void hook_methods(String className, String methodName, XC_MethodHook xmh){
        try {
            Class<?> clazz = Class.forName(className);
            for (Method method : clazz.getDeclaredMethods())
                if (method.getName().equals(methodName)
                        && !Modifier.isAbstract(method.getModifiers())
                        && Modifier.isPublic(method.getModifiers())) {
                    XposedBridge.hookMethod(method, xmh);
                }
        } catch (Exception e) {
            XposedBridge.log(e);
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) throws Throwable {
        mLpp = loadPackageParam;
        hook_method("android.net.wifi.WifiManager", mLpp.classLoader, "getScanResults", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                super.beforeHookedMethod(param);
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                //返回空，强制让apps使用gps定位信息
                param.setResult(null);
            }
        });
        hook_method("android.telephony.TelephonyManager", mLpp.classLoader, "getCellLocation",
                new XC_MethodHook(){
                    /**
                     * android.telephony.TelephonyManager的getCellLocation方法
                     * Returns the current location of the device.
                     * Return null if current location is not available.
                     */
                    @Override
                    protected void afterHookedMethod(MethodHookParam param)
                            throws Throwable {
                        param.setResult(null);
                    }
                });

        hook_method("android.telephony.TelephonyManager", mLpp.classLoader, "getNeighboringCellInfo",
                new XC_MethodHook(){
                    /**
                     * android.telephony.TelephonyManager类的getNeighboringCellInfo方法
                     * Returns the neighboring cell information of the device.
                     */
                    @Override
                    protected void afterHookedMethod(MethodHookParam param)
                            throws Throwable {
                        param.setResult(null);
                    }
                });
        hook_methods("android.location.LocationManager", "requestLocationUpdates",
                new XC_MethodHook() {
                    /**
                     * android.location.LocationManager类的requestLocationUpdates方法
                     * 其参数有4个：
                     * String provider, long minTime, float minDistance,LocationListener listener
                     * Register for location updates using the named provider, and a pending intent
                     */
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {

                        if (param.args.length == 4 && (param.args[0] instanceof String)) {
                            //位置监听器,当位置改变时会触发onLocationChanged方法
                            LocationListener ll = (LocationListener)param.args[3];

                            Class<?> clazz = LocationListener.class;
                            Method m = null;
                            for (Method method : clazz.getDeclaredMethods()) {
                                if (method.getName().equals("onLocationChanged")) {
                                    m = method;
                                    break;
                                }
                            }

                            try {
                                if (m != null) {
                                    Object[] args = new Object[1];
                                    Location l = new Location(LocationManager.GPS_PROVIDER);
                                    //公司地址117.101567,39.086634
                                    double la=117.101567;
                                    double lo=39.086634;
                                    l.setLatitude(la);
                                    l.setLongitude(lo);
                                    args[0] = l;
                                    m.invoke(ll, args);
                                    XposedBridge.log("fake location: " + la + ", " + lo);
                                }
                            } catch (Exception e) {
                                XposedBridge.log(e);
                            }
                        }
                    }
                });


        hook_methods("android.location.LocationManager", "getGpsStatus",
                new XC_MethodHook(){
                    /**
                     * android.location.LocationManager类的getGpsStatus方法
                     * 其参数只有1个：GpsStatus status
                     * Retrieves information about the current status of the GPS engine.
                     * This should only be called from the {@link GpsStatus.Listener#onGpsStatusChanged}
                     * callback to ensure that the data is copied atomically.
                     *
                     */
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        GpsStatus gss = (GpsStatus)param.getResult();
                        if (gss == null)
                            return;

                        Class<?> clazz = GpsStatus.class;
                        Method m = null;
                        for (Method method : clazz.getDeclaredMethods()) {
                            if (method.getName().equals("setStatus")) {
                                if (method.getParameterTypes().length > 1) {
                                    m = method;
                                    break;
                                }
                            }
                        }
                        m.setAccessible(true);
                        //make the apps belive GPS works fine now
                        int svCount = 5;
                        int[] prns = {1, 2, 3, 4, 5};
                        float[] snrs = {0, 0, 0, 0, 0};
                        float[] elevations = {0, 0, 0, 0, 0};
                        float[] azimuths = {0, 0, 0, 0, 0};
                        int ephemerisMask = 0x1f;
                        int almanacMask = 0x1f;
                        //5 satellites are fixed
                        int usedInFixMask = 0x1f;
                        try {
                            if (m != null) {
                                m.invoke(gss,svCount, prns, snrs, elevations, azimuths, ephemerisMask, almanacMask, usedInFixMask);
                                param.setResult(gss);
                            }
                        } catch (Exception e) {
                            XposedBridge.log(e);
                        }
                    }
                });
    }
}
