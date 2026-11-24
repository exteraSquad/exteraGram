/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins.utils;

import android.os.Build;

import androidx.annotation.Keep;
import androidx.annotation.RequiresApi;

import com.android.dx.Code;
import com.android.dx.Comparison;
import com.android.dx.DexMaker;
import com.android.dx.FieldId;
import com.android.dx.Label;
import com.android.dx.Local;
import com.android.dx.MethodId;
import com.android.dx.TypeId;
import com.chaquo.python.PyObject;
import com.exteragram.messenger.utils.AppUtils;

import org.mvel2.MVEL;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.Utilities;

import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import dalvik.system.InMemoryDexClassLoader;

/** @noinspection rawtypes*/
@SuppressWarnings("unchecked")
public class ClassProxy {
    private static final String PYTHON_PEER_FIELD_NAME = "__extera_internal_python_peer__";
    private static final Object[] EMPTY_ARGS = new Object[0];
    private static final ConcurrentHashMap<String, Serializable> mvelExpressionCache = new ConcurrentHashMap<>();
    private static final Object generatedClassLoaderLock = new Object();
    private static volatile ClassLoader sharedGeneratedClassLoader;

    @Keep
    public static void runPythonRunnable(PyObject callback) {
        if (callback == null) {
            AppUtils.log("Runnable callback is null");
            return;
        }

        try {
            callback.call(EMPTY_ARGS);
        } catch (Exception e) {
            AppUtils.log("Error in runnable", e);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public static Class<?> createProxyClass(Class<?> baseClass, Utilities.Callback3Return<Object, String, Object[], Object> handler, List<Method> methodsToOverride, List<Constructor<?>> constructorsToOverride) throws Exception {
        return createProxyClassInternal(baseClass, handler, null, null, methodsToOverride, constructorsToOverride, null, null, null, true);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public static Class<?> createProxyClass(Class<?> baseClass, Utilities.Callback3Return<Object, String, Object[], Object> handler, List<Method> methodsToOverride, List<Constructor<?>> constructorsToOverride, String customName) throws Exception {
        return createProxyClassInternal(baseClass, handler, null, null, methodsToOverride, constructorsToOverride, null, null, customName, true);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public static Class<?> createProxyClass(Class<?> baseClass, Utilities.Callback3Return<Object, String, Object[], Object> handler, List<Method> methodsToOverride, List<Constructor<?>> constructorsToOverride, List<ProxyMethodSpec> methodSpecs, List<FieldSpec> fieldSpecs) throws Exception {
        return createProxyClassInternal(baseClass, handler, null, null, methodsToOverride, constructorsToOverride, methodSpecs, fieldSpecs, null, false);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public static Class<?> createProxyClass(Class<?> baseClass, Utilities.Callback3Return<Object, String, Object[], Object> handler, List<Method> methodsToOverride, List<Constructor<?>> constructorsToOverride, List<ProxyMethodSpec> methodSpecs, List<FieldSpec> fieldSpecs, String customName) throws Exception {
        return createProxyClassInternal(baseClass, handler, null, null, methodsToOverride, constructorsToOverride, methodSpecs, fieldSpecs, customName, false);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public static Class<?> createProxyClass(Class<?> baseClass, Utilities.Callback3Return<Object, String, Object[], Object> handler, DexMakerHook dexMakerHook, List<Class<?>> interfaces, List<Method> methodsToOverride, List<Constructor<?>> constructorsToOverride, List<ProxyMethodSpec> methodSpecs, List<FieldSpec> fieldSpecs) throws Exception {
        return createProxyClassInternal(baseClass, handler, dexMakerHook, interfaces, methodsToOverride, constructorsToOverride, methodSpecs, fieldSpecs, null, false);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public static Class<?> createProxyClass(Class<?> baseClass, Utilities.Callback3Return<Object, String, Object[], Object> handler, DexMakerHook dexMakerHook, List<Class<?>> interfaces, List<Method> methodsToOverride, List<Constructor<?>> constructorsToOverride, List<ProxyMethodSpec> methodSpecs, List<FieldSpec> fieldSpecs, String customName) throws Exception {
        return createProxyClassInternal(baseClass, handler, dexMakerHook, interfaces, methodsToOverride, constructorsToOverride, methodSpecs, fieldSpecs, customName, false);
    }

    @SuppressWarnings("rawtypes")
    @RequiresApi(api = Build.VERSION_CODES.O)
    private static Class<?> createProxyClassInternal(Class<?> baseClass, Utilities.Callback3Return<Object, String, Object[], Object> handler, DexMakerHook dexMakerHook, List<Class<?>> interfaces, List<Method> methodsToOverride, List<Constructor<?>> constructorsToOverride, List<ProxyMethodSpec> methodSpecs, List<FieldSpec> fieldSpecs, String customName, boolean useAllOverridableMethodsByDefault) throws Exception {
        DexMaker dexMaker = new DexMaker();
        String baseName = baseClass.getSimpleName();
        if (baseName.isEmpty()) baseName = "Anonymous";

        String proxyCustomName = sanitizeProxyClassSegment(customName);
        String fullClassName = "com.exteragram.messenger.plugins.Proxy_" + baseName
                + (proxyCustomName != null ? "_" + proxyCustomName : "")
                + "_" + Math.abs(baseClass.getName().hashCode()) + "_" + Math.abs(new Random().nextInt());
        String classPath = "L" + fullClassName.replace('.', '/') + ";";

        TypeId<?> proxyType = TypeId.get(classPath);
        TypeId<?> superType = TypeId.get(baseClass);

        TypeId handlerType = TypeId.get(Utilities.Callback3Return.class);
        FieldId handlerField = proxyType.getField(handlerType, "staticHandler");

        TypeId<?>[] interfaceTypes = toInterfaceTypeIds(interfaces);
        List<TypeId<?>> interfaceTypeList = new ArrayList<>();
        Collections.addAll(interfaceTypeList, interfaceTypes);

        dexMaker.declare(proxyType, "GeneratedByExteraProxy.java", Modifier.PUBLIC, superType, interfaceTypes);
        dexMaker.declare(handlerField, Modifier.PUBLIC | Modifier.STATIC, null);

        if (fieldSpecs != null) {
            for (FieldSpec fieldSpec : fieldSpecs) {
                if (fieldSpec == null || fieldSpec.name == null || fieldSpec.type == null) {
                    continue;
                }

                FieldId fieldId = proxyType.getField(TypeId.get(fieldSpec.type), fieldSpec.name);
                Object staticValue = Modifier.isStatic(fieldSpec.modifiers) ? defaultValueForType(fieldSpec.type) : null;
                dexMaker.declare(fieldId, fieldSpec.modifiers, staticValue);

                if (fieldSpec.methods != null) {
                    for (FieldMethodSpec methodSpec : fieldSpec.methods) {
                        if (methodSpec == null || methodSpec.name == null) {
                            continue;
                        }
                        generateFieldAccessorMethod(dexMaker, proxyType, fieldSpec, methodSpec);
                    }
                }
            }
        }

        Constructor<?>[] constructors = constructorsToOverride != null ? constructorsToOverride.toArray(new Constructor<?>[0]) : baseClass.getDeclaredConstructors();
        for (Constructor<?> c : constructors) {
            if (Modifier.isPrivate(c.getModifiers())) continue;
            generateConstructor(dexMaker, proxyType, superType, c, handlerField);
        }

        Map<String, Method> allOverridableMethods = getAllOverridableMethods(baseClass, interfaces);
        Map<String, Method> overrideMethods = new LinkedHashMap<>();
        if (useAllOverridableMethodsByDefault && methodsToOverride == null && (methodSpecs == null || methodSpecs.isEmpty())) {
            overrideMethods.putAll(allOverridableMethods);
        }
        if (methodsToOverride != null) {
            for (Method method : methodsToOverride) {
                if (method == null) continue;
                overrideMethods.put(buildMethodSignature(method.getName(), method.getParameterTypes()), method);
            }
        }

        List<ProxyMethodSpec> newMethods = new ArrayList<>();
        Map<String, ProxyMethodSpec> overrideImplementations = new LinkedHashMap<>();
        if (methodSpecs != null) {
            for (ProxyMethodSpec methodSpec : methodSpecs) {
                if (methodSpec == null || methodSpec.name == null) continue;

                if (methodSpec.overrideExisting) {
                    if (methodSpec.parameterTypes == null) {
                        for (Method method : allOverridableMethods.values()) {
                            if (methodSpec.name.equals(method.getName())) {
                                String signature = buildMethodSignature(method.getName(), method.getParameterTypes());
                                overrideMethods.put(signature, method);
                                if (methodSpec.isMvel()) {
                                    overrideImplementations.put(signature, methodSpec);
                                }
                            }
                        }
                    } else {
                        Method method = allOverridableMethods.get(buildMethodSignature(methodSpec.name, methodSpec.parameterTypes));
                        if (method == null) {
                            throw new NoSuchMethodException("Unable to resolve overridable method " + methodSpec.name);
                        }
                        String signature = buildMethodSignature(method.getName(), method.getParameterTypes());
                        overrideMethods.put(signature, method);
                        if (methodSpec.isMvel()) {
                            overrideImplementations.put(signature, methodSpec);
                        }
                    }
                } else {
                    newMethods.add(methodSpec);
                }
            }
        }

        for (Map.Entry<String, Method> overrideEntry : overrideMethods.entrySet()) {
            Method m = overrideEntry.getValue();
            generateProxyMethod(dexMaker, proxyType, m, handlerField, true);
            ProxyMethodSpec implementationSpec = overrideImplementations.get(overrideEntry.getKey());
            if (implementationSpec != null && implementationSpec.isMvel()) {
                generateMvelMethod(
                        dexMaker,
                        proxyType,
                        m.getName(),
                        m.getReturnType(),
                        m.getParameterTypes(),
                        Modifier.PUBLIC,
                        implementationSpec.mvelCode,
                        implementationSpec.argumentNames
                );
            } else {
                generateProxyMethod(dexMaker, proxyType, m, handlerField, false);
            }
        }

        for (ProxyMethodSpec methodSpec : newMethods) {
            if (methodSpec.isMvel()) {
                generateMvelMethod(
                        dexMaker,
                        proxyType,
                        methodSpec.name,
                        methodSpec.returnType != null ? methodSpec.returnType : Void.TYPE,
                        methodSpec.parameterTypes != null ? methodSpec.parameterTypes : new Class<?>[0],
                        methodSpec.modifiers != 0 ? methodSpec.modifiers : Modifier.PUBLIC,
                        methodSpec.mvelCode,
                        methodSpec.argumentNames
                );
            } else {
                generateHandlerMethod(
                        dexMaker,
                        proxyType,
                        methodSpec.name,
                        methodSpec.returnType != null ? methodSpec.returnType : Void.TYPE,
                        methodSpec.parameterTypes != null ? methodSpec.parameterTypes : new Class<?>[0],
                        methodSpec.modifiers != 0 ? methodSpec.modifiers : Modifier.PUBLIC,
                        handlerField
                );
            }
        }

        if (dexMakerHook != null) {
            dexMakerHook.apply(dexMaker, proxyType, superType, interfaceTypeList);
        }

        byte[] dexBytes = dexMaker.generate();
        Class<?> proxyClass;
        synchronized (generatedClassLoaderLock) {
            ClassLoader parentLoader = sharedGeneratedClassLoader;
            if (parentLoader == null) {
                parentLoader = ApplicationLoader.applicationContext.getClassLoader();
            }

            InMemoryDexClassLoader loader = new InMemoryDexClassLoader(
                    ByteBuffer.wrap(dexBytes),
                    parentLoader
            );

            proxyClass = loader.loadClass(fullClassName);
            sharedGeneratedClassLoader = loader;
        }

        java.lang.reflect.Field field = proxyClass.getDeclaredField("staticHandler");
        field.setAccessible(true);
        field.set(null, handler);

        return proxyClass;
    }

    private static String sanitizeProxyClassSegment(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }

        StringBuilder result = new StringBuilder(normalized.length());
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (Character.isLetterOrDigit(ch) || ch == '_') {
                result.append(ch);
            } else {
                result.append('_');
            }
        }
        return result.toString();
    }

    private static TypeId<?>[] toInterfaceTypeIds(List<Class<?>> interfaces) {
        if (interfaces == null || interfaces.isEmpty()) {
            return new TypeId<?>[0];
        }

        ArrayList<TypeId<?>> resolvedInterfaces = new ArrayList<>();
        for (Class<?> iface : interfaces) {
            if (iface == null) {
                continue;
            }
            if (!iface.isInterface()) {
                throw new IllegalArgumentException("Expected interface, got " + iface.getName());
            }
            resolvedInterfaces.add(TypeId.get(iface));
        }
        return resolvedInterfaces.toArray(new TypeId<?>[0]);
    }

    private static Map<String, Method> getAllOverridableMethods(Class<?> clazz, List<Class<?>> interfaces) {
        Map<String, Method> methods = new LinkedHashMap<>();
        collectOverridableMethods(clazz, methods);
        if (interfaces != null) {
            for (Class<?> iface : interfaces) {
                collectOverridableMethods(iface, methods);
            }
        }
        return methods;
    }

    private static void collectOverridableMethods(Class<?> clazz, Map<String, Method> methods) {
        if (clazz == null || clazz == Object.class) {
            return;
        }

        for (Method m : clazz.getDeclaredMethods()) {
            int mod = m.getModifiers();

            if (!Modifier.isStatic(mod) && !Modifier.isFinal(mod) &&
                    (Modifier.isPublic(mod) || Modifier.isProtected(mod))) {

                String key = buildMethodSignature(m.getName(), m.getParameterTypes());
                if (!methods.containsKey(key)) {
                    methods.put(key, m);
                }
            }
        }

        for (Class<?> iface : clazz.getInterfaces()) {
            collectOverridableMethods(iface, methods);
        }

        collectOverridableMethods(clazz.getSuperclass(), methods);
    }

    private static String buildMethodSignature(String name, Class<?>[] parameterTypes) {
        StringBuilder signature = new StringBuilder(name);
        if (parameterTypes != null) {
            for (Class<?> param : parameterTypes) {
                signature.append(':').append(param.getName());
            }
        }
        return signature.toString();
    }

    private static Object defaultValueForType(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == long.class) return 0L;
        if (type == double.class) return 0.0d;
        if (type == float.class) return 0.0f;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        if (type == char.class) return '\0';
        return 0;
    }

    @SuppressWarnings("rawtypes")
    private static void generateConstructor(DexMaker dex, TypeId<?> proxyType, TypeId<?> superType, Constructor<?> c, FieldId handlerField) {
        Class<?>[] params = c.getParameterTypes();
        TypeId<?>[] paramTypes = new TypeId[params.length];
        for (int i = 0; i < params.length; i++) paramTypes[i] = TypeId.get(params[i]);

        MethodId constructorId = proxyType.getConstructor(paramTypes);
        Code code = dex.declare(constructorId, Modifier.PUBLIC);

        TypeId handlerType = TypeId.get(Utilities.Callback3Return.class);
        MethodId runMethod = handlerType.getMethod(TypeId.OBJECT, "run", TypeId.OBJECT, TypeId.OBJECT, TypeId.OBJECT);

        Local thisRef = code.getThis(proxyType);
        Local handler = code.newLocal(handlerType);
        Local name = code.newLocal(TypeId.STRING);
        Local argsArray = code.newLocal(TypeId.get(Object[].class));
        Local nullRef = code.newLocal(TypeId.OBJECT);
        Local resultObj = code.newLocal(TypeId.OBJECT);
        Local resultCast = code.newLocal(TypeId.get(Object[].class));
        Local size = code.newLocal(TypeId.INT);
        Local tmpIdx = code.newLocal(TypeId.INT);
        Local[] boxedLocals = new Local[params.length];
        Local boxedRes = code.newLocal(TypeId.OBJECT);
        for (int i = 0; i < params.length; i++) boxedLocals[i] = code.newLocal(TypeId.OBJECT);

        Local zero = code.newLocal(TypeId.INT);

        Local[] superArgs = new Local[params.length];
        for (int i = 0; i < params.length; i++) superArgs[i] = code.newLocal(paramTypes[i]);

        code.loadConstant(zero, 0);
        code.newArray(argsArray, zero);
        code.move(resultCast, argsArray);
        code.sget(handlerField, handler);
        code.loadConstant(name, buildMethodSignature("<constructor>", params));
        code.loadConstant(nullRef, null);
        code.loadConstant(tmpIdx, 0);
        code.loadConstant(boxedRes, null);

        for (int i = 0; i < params.length; i++) {
            code.move(superArgs[i], code.getParameter(i, paramTypes[i]));
        }

        code.loadConstant(size, params.length);
        code.newArray(argsArray, size);

        for (int i = 0; i < params.length; i++) {
            MethodId boxMethod = TypeId.get(TypeHelper.class).getMethod(TypeId.OBJECT, "box", params[i].isPrimitive() ? paramTypes[i] : TypeId.OBJECT);
            code.invokeStatic(boxMethod, boxedLocals[i], superArgs[i]);
            code.loadConstant(tmpIdx, i);
            code.aput(argsArray, tmpIdx, boxedLocals[i]);
        }

        code.invokeInterface(runMethod, resultObj, handler, nullRef, name, argsArray);

        Label useOriginal = new Label();
        code.compare(Comparison.EQ, useOriginal, resultObj, nullRef);

        code.cast(resultCast, resultObj);
        for (int i = 0; i < params.length; i++) {
            code.loadConstant(tmpIdx, i);
            code.aget(boxedRes, resultCast, tmpIdx);

            if (params[i].isPrimitive()) {
                String unboxName = "unbox" + capitalize(params[i].getName());
                MethodId unboxMethod = TypeId.get(TypeHelper.class).getMethod(paramTypes[i], unboxName, TypeId.OBJECT);
                code.invokeStatic(unboxMethod, superArgs[i], boxedRes);
            } else {
                code.cast(superArgs[i], boxedRes);
            }
        }

        code.mark(useOriginal);

        code.invokeDirect(superType.getConstructor(paramTypes), null, thisRef, superArgs);
        code.invokeInterface(runMethod, null, handler, thisRef, name, argsArray);

        code.returnVoid();
    }

    @SuppressWarnings("rawtypes")
    private static void generateProxyMethod(DexMaker dex, TypeId<?> proxyType, Method method, FieldId handlerField, boolean isSuperBridge) {
        Class<?>[] params = method.getParameterTypes();
        TypeId<?>[] paramTypes = new TypeId[params.length];
        for (int i = 0; i < params.length; i++) paramTypes[i] = TypeId.get(params[i]);

        TypeId<?> returnType = TypeId.get(method.getReturnType());
        String methodName = isSuperBridge ? "SUPER_" + method.getName() : method.getName();

        if (isSuperBridge) {
            MethodId methodId = proxyType.getMethod(returnType, methodName, paramTypes);
            Code code = dex.declare(methodId, Modifier.PUBLIC);

            Local thisRef = code.getThis(proxyType);
            Local result = returnType.equals(TypeId.VOID) ? null : code.newLocal(returnType);
            Local[] args = new Local[params.length];
            for (int i = 0; i < params.length; i++) args[i] = code.getParameter(i, paramTypes[i]);
            MethodId superMethodId = TypeId.get(method.getDeclaringClass()).getMethod(returnType, method.getName(), paramTypes);
            code.invokeSuper(superMethodId, result, thisRef, args);
            if (returnType.equals(TypeId.VOID)) code.returnVoid();
            else code.returnValue(result);
            return;
        }

        generateHandlerMethod(dex, proxyType, methodName, method.getReturnType(), params, Modifier.PUBLIC, handlerField);
    }

    @SuppressWarnings("rawtypes")
    private static void generateHandlerMethod(DexMaker dex, TypeId<?> proxyType, String methodName, Class<?> methodReturnType, Class<?>[] params, int modifiers, FieldId handlerField) {
        TypeId<?>[] paramTypes = new TypeId[params.length];
        for (int i = 0; i < params.length; i++) paramTypes[i] = TypeId.get(params[i]);

        TypeId handlerType = TypeId.get(Utilities.Callback3Return.class);
        MethodId<?, Object> runMethod = handlerType.getMethod(
                TypeId.OBJECT,
                "run",
                TypeId.OBJECT,
                TypeId.OBJECT,
                TypeId.OBJECT
        );

        TypeId<?> returnType = TypeId.get(methodReturnType);

        MethodId methodId = proxyType.getMethod(returnType, methodName, paramTypes);
        Code code = dex.declare(methodId, modifiers);

        Local thisRef = code.getThis(proxyType);
        Local result = null;
        if (!returnType.equals(TypeId.VOID)) result = code.newLocal(returnType);

        Local handler = code.newLocal(TypeId.get(Utilities.Callback3Return.class));
        Local name = code.newLocal(TypeId.STRING);
        Local argsArray = code.newLocal(TypeId.get(Object[].class));
        Local size = code.newLocal(TypeId.INT);
        Local resultObj = code.newLocal(TypeId.OBJECT);
        Local tmpIdx = code.newLocal(TypeId.INT);

        Local[] boxedLocals = new Local[params.length];
        for (int i = 0; i < params.length; i++) {
            boxedLocals[i] = code.newLocal(TypeId.OBJECT);
        }

        code.sget(handlerField, handler);
        code.loadConstant(name, buildMethodSignature(methodName, params));

        code.loadConstant(size, params.length);
        code.newArray(argsArray, size);
        for (int i = 0; i < params.length; i++) {
            Local param = code.getParameter(i, paramTypes[i]);
            MethodId boxMethod = TypeId.get(TypeHelper.class).getMethod(TypeId.OBJECT, "box", params[i].isPrimitive() ? paramTypes[i] : TypeId.OBJECT);
            code.invokeStatic(boxMethod, boxedLocals[i], param);
            code.loadConstant(tmpIdx, i);
            code.aput(argsArray, tmpIdx, boxedLocals[i]);
        }

        code.invokeInterface(runMethod, resultObj, handler, thisRef, name, argsArray);

        if (returnType.equals(TypeId.VOID)) {
            code.returnVoid();
        } else {
            if (methodReturnType.isPrimitive()) {
                String unboxName = "unbox" + capitalize(methodReturnType.getName());
                MethodId unbox = TypeId.get(TypeHelper.class).getMethod(returnType, unboxName, TypeId.OBJECT);
                code.invokeStatic(unbox, result, resultObj);
            } else {
                code.cast(result, resultObj);
            }
            code.returnValue(result);
        }
    }

    @SuppressWarnings("rawtypes")
    private static void generateMvelMethod(DexMaker dex, TypeId<?> proxyType, String methodName, Class<?> methodReturnType, Class<?>[] params, int modifiers, String mvelCode, List<String> argumentNames) {
        TypeId<?>[] paramTypes = new TypeId[params.length];
        for (int i = 0; i < params.length; i++) paramTypes[i] = TypeId.get(params[i]);

        TypeId<?> returnType = TypeId.get(methodReturnType);
        MethodId methodId = proxyType.getMethod(returnType, methodName, paramTypes);
        Code code = dex.declare(methodId, modifiers);

        Local thisRef = Modifier.isStatic(modifiers) ? null : code.getThis(proxyType);
        Local javaRef = code.newLocal(TypeId.OBJECT);
        Local pythonRef = code.newLocal(TypeId.OBJECT);
        Local resultObj = code.newLocal(TypeId.OBJECT);
        Local mvelSource = code.newLocal(TypeId.STRING);
        Local argsArray = code.newLocal(TypeId.get(Object[].class));
        Local argNamesArray = code.newLocal(TypeId.get(String[].class));
        Local size = code.newLocal(TypeId.INT);
        Local tmpIdx = code.newLocal(TypeId.INT);
        Local nullObject = code.newLocal(TypeId.OBJECT);
        Local nullArgNames = code.newLocal(TypeId.get(String[].class));
        Local argNameValue = code.newLocal(TypeId.STRING);
        Local result = returnType.equals(TypeId.VOID) ? null : code.newLocal(returnType);
        Local[] boxedLocals = new Local[params.length];
        for (int i = 0; i < params.length; i++) {
            boxedLocals[i] = code.newLocal(TypeId.OBJECT);
        }

        code.loadConstant(nullObject, null);
        code.move(javaRef, nullObject);
        code.move(pythonRef, nullObject);

        if (!Modifier.isStatic(modifiers)) {
            FieldId pythonPeerField = proxyType.getField(TypeId.OBJECT, PYTHON_PEER_FIELD_NAME);
            code.cast(javaRef, thisRef);
            code.iget(pythonPeerField, pythonRef, thisRef);
        }

        code.loadConstant(mvelSource, mvelCode);
        code.loadConstant(size, params.length);
        code.newArray(argsArray, size);

        for (int i = 0; i < params.length; i++) {
            Local param = code.getParameter(i, paramTypes[i]);
            MethodId boxMethod = TypeId.get(TypeHelper.class).getMethod(TypeId.OBJECT, "box", params[i].isPrimitive() ? paramTypes[i] : TypeId.OBJECT);
            code.invokeStatic(boxMethod, boxedLocals[i], param);
            code.loadConstant(tmpIdx, i);
            code.aput(argsArray, tmpIdx, boxedLocals[i]);
        }

        if (argumentNames != null && !argumentNames.isEmpty()) {
            code.loadConstant(size, argumentNames.size());
            code.newArray(argNamesArray, size);

            for (int i = 0; i < argumentNames.size(); i++) {
                code.loadConstant(argNameValue, argumentNames.get(i));
                code.loadConstant(tmpIdx, i);
                code.aput(argNamesArray, tmpIdx, argNameValue);
            }
        } else {
            code.loadConstant(nullArgNames, null);
            code.move(argNamesArray, nullArgNames);
        }

        MethodId executeMvelMethod = TypeId.get(ClassProxy.class).getMethod(
                TypeId.OBJECT,
                "executeMvelMethod",
                TypeId.OBJECT,
                TypeId.OBJECT,
                TypeId.STRING,
                TypeId.get(String[].class),
                TypeId.get(Object[].class)
        );
        code.invokeStatic(executeMvelMethod, resultObj, javaRef, pythonRef, mvelSource, argNamesArray, argsArray);

        if (returnType.equals(TypeId.VOID)) {
            code.returnVoid();
        } else {
            if (methodReturnType.isPrimitive()) {
                String unboxName = "unbox" + capitalize(methodReturnType.getName());
                MethodId unbox = TypeId.get(TypeHelper.class).getMethod(returnType, unboxName, TypeId.OBJECT);
                code.invokeStatic(unbox, result, resultObj);
            } else {
                code.cast(result, resultObj);
            }
            code.returnValue(result);
        }
    }

    @SuppressWarnings("rawtypes")
    private static void generateFieldAccessorMethod(DexMaker dex, TypeId<?> proxyType, FieldSpec fieldSpec, FieldMethodSpec methodSpec) {
        TypeId<?> fieldType = TypeId.get(fieldSpec.type);
        FieldId fieldId = proxyType.getField(fieldType, fieldSpec.name);
        boolean isStaticField = Modifier.isStatic(fieldSpec.modifiers);
        int methodModifiers = methodSpec.modifiers != 0 ? methodSpec.modifiers : Modifier.PUBLIC;

        if (methodSpec.getter) {
            MethodId methodId = proxyType.getMethod(fieldType, methodSpec.name);
            Code code = dex.declare(methodId, methodModifiers);
            Local result = code.newLocal(fieldType);

            if (isStaticField) {
                code.sget(fieldId, result);
            } else {
                Local thisRef = code.getThis(proxyType);
                code.iget(fieldId, result, thisRef);
            }

            code.returnValue(result);
            return;
        }

        MethodId methodId = proxyType.getMethod(TypeId.VOID, methodSpec.name, fieldType);
        Code code = dex.declare(methodId, methodModifiers);
        Local value = code.getParameter(0, fieldType);

        if (isStaticField) {
            code.sput(fieldId, value);
        } else {
            Local thisRef = code.getThis(proxyType);
            code.iput(fieldId, thisRef, value);
        }

        code.returnVoid();
    }

    public static Object executeMvelMethod(Object javaInstance, Object pythonInstance, String mvelCode, String[] argumentNames, Object[] args) {
        Map<String, Object> vars = new HashMap<>();
        Object[] safeArgs = args != null ? args : new Object[0];

        vars.put("java", javaInstance);
        vars.put("python", pythonInstance);
        vars.put("py", pythonInstance);
        vars.put("self", pythonInstance);
        vars.put("args", safeArgs);
        vars.put("argc", safeArgs.length);

        for (int i = 0; i < safeArgs.length; i++) {
            Object value = safeArgs[i];
            vars.put("arg" + i, value);

            if (argumentNames != null && i < argumentNames.length) {
                String argumentName = argumentNames[i];
                if (argumentName != null && !argumentName.isEmpty()) {
                    vars.put(argumentName, value);
                }
            }
        }

        Serializable compiled = mvelExpressionCache.computeIfAbsent(mvelCode, MVEL::compileExpression);
        return MVEL.executeExpression(compiled, javaInstance, vars);
    }

    private static String capitalize(String s) {
        if (s.equals("int")) return "Int";
        if (s.equals("boolean")) return "Bool";
        if (s.equals("short")) return "Short";
        if (s.equals("byte")) return "Byte";
        if (s.equals("char")) return "Char";
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    public interface DexMakerHook {
        void apply(DexMaker dexMaker, TypeId<?> proxyType, TypeId<?> superType, List<TypeId<?>> interfaces);
    }

    public record ProxyMethodSpec(String name, Class<?> returnType, Class<?>[] parameterTypes,
                                  int modifiers, boolean overrideExisting, String implementation,
                                  String mvelCode, List<String> argumentNames) {
            public ProxyMethodSpec(String name, Class<?> returnType, Class<?>[] parameterTypes, int modifiers, boolean overrideExisting) {
                this(name, returnType, parameterTypes, modifiers, overrideExisting, "python", null, null);
            }

        public boolean isMvel() {
                return "mvel".equals(implementation) && mvelCode != null;
            }
        }

    public record FieldSpec(String name, Class<?> type, int modifiers,
                            List<FieldMethodSpec> methods) {
            public FieldSpec(String name, Class<?> type, int modifiers) {
                this(name, type, modifiers, null);
            }

    }

    public record FieldMethodSpec(String name, int modifiers, boolean getter) {
    }

    public class TypeHelper {
        public static Object box(int v) {
            return Integer.valueOf(v);
        }

        public static Object box(boolean v) {
            return Boolean.valueOf(v);
        }

        public static Object box(long v) {
            return Long.valueOf(v);
        }

        public static Object box(double v) {
            return Double.valueOf(v);
        }

        public static Object box(float v) {
            return Float.valueOf(v);
        }

        public static Object box(short v) {
            return Short.valueOf(v);
        }

        public static Object box(byte v) {
            return Byte.valueOf(v);
        }

        public static Object box(char v) {
            return Character.valueOf(v);
        }

        public static Object box(Object v) {
            return v;
        }

        public static int unboxInt(Object v) {
            return v instanceof Number ? ((Number) v).intValue() : 0;
        }

        public static boolean unboxBool(Object v) {
            return v instanceof Boolean ? (Boolean) v : false;
        }

        public static long unboxLong(Object v) {
            return v instanceof Number ? ((Number) v).longValue() : 0L;
        }

        public static double unboxDouble(Object v) {
            return v instanceof Number ? ((Number) v).doubleValue() : 0.0;
        }

        public static float unboxFloat(Object v) {
            return v instanceof Number ? ((Number) v).floatValue() : 0.0f;
        }

        public static short unboxShort(Object v) {
            return v instanceof Number ? ((Number) v).shortValue() : 0;
        }

        public static byte unboxByte(Object v) {
            return v instanceof Number ? ((Number) v).byteValue() : 0;
        }

        public static char unboxChar(Object v) {
            return v instanceof Character ? (Character) v : 0;
        }
    }
}
