package org.rapidoid.cls;

/*
 * #%L
 * rapidoid-commons
 * %%
 * Copyright (C) 2014 - 2016 Nikolche Mihajlovski and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.LocalVariableAttribute;
import javassist.bytecode.MethodInfo;
import org.rapidoid.commons.Coll;
import org.rapidoid.commons.Dates;
import org.rapidoid.commons.Err;
import org.rapidoid.u.U;
import org.rapidoid.var.Var;
import org.rapidoid.var.Vars;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

/**
 * @author Nikolche Mihajlovski
 * @since 2.0.0
 */
public class Cls {

	private static Pattern JRE_CLASS_PATTERN = Pattern
			.compile("^(java|javax|javafx|com\\.sun|sun|com\\.oracle|oracle|jdk|org\\.omg|org\\.w3c).*");

	private static final Map<Class<?>, Class<?>> PRIMITIVE_WRAPPERS = U.map(boolean.class, Boolean.class, byte.class,
			Byte.class, char.class, Character.class, double.class, Double.class, float.class, Float.class, int.class,
			Integer.class, long.class, Long.class, short.class, Short.class, void.class, Void.class);

	private static final Map<String, TypeKind> KINDS = initKinds();

	private static final Object[] EMPTY_ARRAY = {};

	private Cls() {
	}

	protected static Map<String, TypeKind> initKinds() {

		Map<String, TypeKind> kinds = new HashMap<String, TypeKind>();

		kinds.put("boolean", TypeKind.BOOLEAN);

		kinds.put("byte", TypeKind.BYTE);

		kinds.put("char", TypeKind.CHAR);

		kinds.put("short", TypeKind.SHORT);

		kinds.put("int", TypeKind.INT);

		kinds.put("long", TypeKind.LONG);

		kinds.put("float", TypeKind.FLOAT);

		kinds.put("double", TypeKind.DOUBLE);

		kinds.put("java.lang.String", TypeKind.STRING);

		kinds.put("java.lang.Boolean", TypeKind.BOOLEAN_OBJ);

		kinds.put("java.lang.Byte", TypeKind.BYTE_OBJ);

		kinds.put("java.lang.Character", TypeKind.CHAR_OBJ);

		kinds.put("java.lang.Short", TypeKind.SHORT_OBJ);

		kinds.put("java.lang.Integer", TypeKind.INT_OBJ);

		kinds.put("java.lang.Long", TypeKind.LONG_OBJ);

		kinds.put("java.lang.Float", TypeKind.FLOAT_OBJ);

		kinds.put("java.lang.Double", TypeKind.DOUBLE_OBJ);

		kinds.put("java.util.Date", TypeKind.DATE);

		kinds.put("java.util.UUID", TypeKind.UUID);

		return kinds;
	}

	/**
	 * @return Any kind, except NULL
	 */
	public static TypeKind kindOf(Class<?> type) {
		String typeName = type.getName();
		TypeKind kind = KINDS.get(typeName);

		if (kind == null) {
			kind = TypeKind.OBJECT;
		}

		return kind;
	}

	/**
	 * @return Any kind, including NULL
	 */
	public static TypeKind kindOf(Object value) {
		if (value == null) {
			return TypeKind.NULL;
		}

		String typeName = value.getClass().getName();
		TypeKind kind = KINDS.get(typeName);

		if (kind == null) {
			kind = TypeKind.OBJECT;
		}

		return kind;
	}

	public static void setFieldValue(Object instance, String fieldName, Object value) {
		try {
			for (Class<?> c = instance.getClass(); c.getSuperclass() != null; c = c.getSuperclass()) {
				try {
					Field field = c.getDeclaredField(fieldName);
					field.setAccessible(true);
					field.set(instance, value);
					field.setAccessible(false);
					return;
				} catch (NoSuchFieldException e) {
					// keep searching the filed in the super-class...
				}
			}
		} catch (Exception e) {
			throw U.rte("Cannot set field value!", e);
		}

		throw U.rte("Cannot find the field '%s' in the class '%s'", fieldName, instance.getClass());
	}

	public static void setFieldValue(Field field, Object instance, Object value) {
		try {
			field.setAccessible(true);
			field.set(instance, value);
			field.setAccessible(false);
		} catch (Exception e) {
			throw U.rte("Cannot set field value!", e);
		}
	}

	@SuppressWarnings("unchecked")
	public static <T> T getFieldValue(Object instance, String fieldName, T defaultValue) {
		try {
			return (T) getFieldValue(instance, fieldName);
		} catch (Exception e) {
			return defaultValue;
		}
	}

	public static Object getFieldValue(Object instance, String fieldName) {
		try {
			for (Class<?> c = instance.getClass(); c.getSuperclass() != null; c = c.getSuperclass()) {
				try {
					Field field = c.getDeclaredField(fieldName);
					return getFieldValue(field, instance);
				} catch (NoSuchFieldException e) {
					// keep searching the filed in the super-class...
				}
			}
		} catch (Exception e) {
			throw U.rte("Cannot get field value!", e);
		}

		throw U.rte("Cannot find the field '%s' in the class '%s'", fieldName, instance.getClass());
	}

	public static Object getFieldValue(Field field, Object instance) {
		try {
			field.setAccessible(true);
			Object value = field.get(instance);
			field.setAccessible(false);

			return value;
		} catch (Exception e) {
			throw U.rte("Cannot get field value!", e);
		}
	}

	public static List<Annotation> getAnnotations(Class<?> clazz) {
		List<Annotation> allAnnotations = U.list();

		try {
			for (Class<?> c = clazz; c.getSuperclass() != null; c = c.getSuperclass()) {
				Annotation[] annotations = c.getDeclaredAnnotations();
				for (Annotation an : annotations) {
					allAnnotations.add(an);
				}
			}

		} catch (Exception e) {
			throw U.rte("Cannot get annotations!", e);
		}

		return allAnnotations;
	}

	public static List<Field> getFields(Class<?> clazz) {
		List<Field> allFields = U.list();

		try {
			for (Class<?> c = clazz; c.getSuperclass() != null; c = c.getSuperclass()) {
				Field[] fields = c.getDeclaredFields();
				for (Field field : fields) {
					allFields.add(field);
				}
			}

		} catch (Exception e) {
			throw U.rte("Cannot get fields!", e);
		}

		return allFields;
	}

	public static List<Field> getFieldsAnnotated(Class<?> clazz, Class<? extends Annotation> annotation) {
		List<Field> annotatedFields = U.list();

		try {
			for (Class<?> c = clazz; c.getSuperclass() != null; c = c.getSuperclass()) {
				Field[] fields = c.getDeclaredFields();
				for (Field field : fields) {
					if (field.isAnnotationPresent(annotation)) {
						annotatedFields.add(field);
					}
				}
			}

		} catch (Exception e) {
			throw U.rte("Cannot get annotated fields!", e);
		}

		return annotatedFields;
	}

	public static List<Method> getMethods(Class<?> clazz) {
		List<Method> methods = U.list();

		try {
			for (Class<?> c = clazz; c.getSuperclass() != null; c = c.getSuperclass()) {
				for (Method method : c.getDeclaredMethods()) {
					methods.add(method);
				}
			}

		} catch (Exception e) {
			throw U.rte("Cannot get methods!", e);
		}

		return methods;
	}

	public static List<Method> getMethodsAnnotated(Class<?> clazz, Class<? extends Annotation> annotation) {
		List<Method> annotatedMethods = U.list();

		try {
			for (Class<?> c = clazz; c.getSuperclass() != null; c = c.getSuperclass()) {
				Method[] methods = c.getDeclaredMethods();
				for (Method method : methods) {
					if (method.isAnnotationPresent(annotation)) {
						annotatedMethods.add(method);
					}
				}
			}

		} catch (Exception e) {
			throw U.rte("Cannot instantiate class!", e);
		}

		return annotatedMethods;
	}

	public static List<Method> getMethodsNamed(Class<?> clazz, String name) {
		List<Method> annotatedMethods = U.list();

		try {
			for (Class<?> c = clazz; c.getSuperclass() != null; c = c.getSuperclass()) {
				Method[] methods = c.getDeclaredMethods();
				for (Method method : methods) {
					if (method.getName().equals(name)) {
						annotatedMethods.add(method);
					}
				}
			}

		} catch (Exception e) {
			throw U.rte("Cannot instantiate class!", e);
		}

		return annotatedMethods;
	}

	public static Method getMethod(Class<?> clazz, String name, Class<?>... parameterTypes) {
		try {
			return clazz.getMethod(name, parameterTypes);
		} catch (NoSuchMethodException e) {
			try {
				return clazz.getDeclaredMethod(name, parameterTypes);
			} catch (NoSuchMethodException e1) {
				throw U.rte("Cannot find method: %s", e, name);
			}
		} catch (SecurityException e) {
			throw U.rte("Cannot access method: %s", e, name);
		}
	}

	public static Method findMethod(Class<?> clazz, String name, Class<?>... parameterTypes) {
		try {
			return clazz.getMethod(name, parameterTypes);
		} catch (NoSuchMethodException e) {
			try {
				return clazz.getDeclaredMethod(name, parameterTypes);
			} catch (NoSuchMethodException e1) {
				return null;
			}
		} catch (SecurityException e) {
			return null;
		}
	}

	public static Field getField(Class<?> clazz, String name) {
		try {
			return clazz.getField(name);
		} catch (NoSuchFieldException e) {
			throw U.rte("Cannot find field: %s", e, name);
		} catch (SecurityException e) {
			throw U.rte("Cannot access field: %s", e, name);
		}
	}

	public static Field findField(Class<?> clazz, String name) {
		try {
			return clazz.getField(name);
		} catch (NoSuchFieldException e) {
			return null;
		} catch (SecurityException e) {
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	public static <T> T invokeStatic(Method m, Object... args) {
		boolean accessible = m.isAccessible();
		try {
			m.setAccessible(true);
			return (T) m.invoke(null, args);
		} catch (IllegalAccessException e) {
			throw U.rte("Cannot statically invoke method '%s' with args: %s", e, m.getName(), Arrays.toString(args));
		} catch (IllegalArgumentException e) {
			throw U.rte("Cannot statically invoke method '%s' with args: %s", e, m.getName(), Arrays.toString(args));
		} catch (InvocationTargetException e) {
			throw U.rte("Cannot statically invoke method '%s' with args: %s", e, m.getName(), Arrays.toString(args));
		} finally {
			m.setAccessible(accessible);
		}
	}

	@SuppressWarnings("unchecked")
	public static <T> T invoke(Method m, Object target, Object... args) {
		boolean accessible = m.isAccessible();
		try {
			m.setAccessible(true);
			return (T) m.invoke(target, args);
		} catch (Exception e) {
			throw U.rte("Cannot invoke method '%s' with args: %s", e, m.getName(), Arrays.toString(args));
		} finally {
			m.setAccessible(accessible);
		}
	}

	public static Class<?>[] getImplementedInterfaces(Class<?> clazz) {
		try {
			List<Class<?>> interfaces = new LinkedList<Class<?>>();

			for (Class<?> c = clazz; c.getSuperclass() != null; c = c.getSuperclass()) {
				for (Class<?> interf : c.getInterfaces()) {
					interfaces.add(interf);
				}
			}

			return interfaces.toArray(new Class<?>[interfaces.size()]);
		} catch (Exception e) {
			throw U.rte("Cannot retrieve implemented interfaces!", e);
		}
	}

	public static boolean annotatedMethod(Object instance, String methodName, Class<Annotation> annotation) {
		try {
			Method method = instance.getClass().getMethod(methodName);
			return method.getAnnotation(annotation) != null;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static <T> Constructor<T> getConstructor(Class<T> clazz, Class<?>... paramTypes) {
		try {
			return (Constructor<T>) clazz.getConstructor(paramTypes);
		} catch (Exception e) {
			throw U.rte("Cannot find the constructor for %s with param types: %s", e, clazz,
					Arrays.toString(paramTypes));
		}
	}

	@SuppressWarnings("unchecked")
	public static <T> T newInstance(Class<T> clazz, Map<String, Object> properties) {
		if (U.isEmpty(properties)) {
			return newInstance(clazz);
		}

		Collection<Object> values = properties.values();

		for (Constructor<?> constr : clazz.getConstructors()) {
			Class<?>[] paramTypes = constr.getParameterTypes();
			Object[] args = getAssignableArgs(paramTypes, values);

			if (args != null) {
				try {
					return (T) constr.newInstance(args);
				} catch (Exception e) {
					throw U.rte(e);
				}
			}
		}

		throw U.rte("Cannot find appropriate constructor for %s with args %s!", clazz, values);
	}

	private static Object[] getAssignableArgs(Class<?>[] types, Collection<?> properties) {
		Object[] args = new Object[types.length];

		for (int i = 0; i < types.length; i++) {
			Class<?> type = types[i];
			args[i] = getUniqueInstanceOf(type, properties);
		}

		return args;
	}

	@SuppressWarnings("unchecked")
	public static <T> T getUniqueInstanceOf(Class<T> type, Collection<?> values) {
		T instance = null;

		for (Object obj : values) {
			if (instanceOf(obj, type)) {
				if (instance == null) {
					instance = (T) obj;
				} else {
					throw U.rte("Found more than one instance of %s: %s and %s", type, instance, obj);
				}
			}
		}

		return instance;
	}

	public static Object[] instantiateAll(Class<?>... classes) {
		Object[] instances = new Object[classes.length];

		for (int i = 0; i < instances.length; i++) {
			instances[i] = newInstance(classes[i]);
		}

		return instances;
	}

	public static Object[] instantiateAll(Collection<Class<?>> classes) {
		if (classes.isEmpty()) {
			return EMPTY_ARRAY;
		}
		Object[] instances = new Object[classes.size()];

		int i = 0;
		for (Class<?> clazz : classes) {
			instances[i++] = newInstance(clazz);
		}

		return instances;
	}

	public static ClassLoader classLoader() {
		return Thread.currentThread().getContextClassLoader();
	}

	@SuppressWarnings("unchecked")
	public static <T> T convert(String value, Class<T> toType) {

		if (value == null) {
			return null;
		}

		if (toType.equals(Object.class)) {
			return (T) value;
		}

		if (Enum.class.isAssignableFrom(toType)) {
			T[] ens = toType.getEnumConstants();
			Enum<?> en;
			for (T t : ens) {
				en = (Enum<?>) t;
				if (en.name().equalsIgnoreCase(value)) {
					return (T) en;
				}
			}

			throw U.rte("Cannot find the enum constant: %s.%s", toType, value);
		}

		TypeKind targetKind = Cls.kindOf(toType);

		switch (targetKind) {

			case NULL:
				throw Err.notExpected();

			case BOOLEAN:
			case BOOLEAN_OBJ:
				if ("y".equalsIgnoreCase(value) || "t".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value)
						|| "true".equalsIgnoreCase(value)) {
					return (T) Boolean.TRUE;
				}

				if ("n".equalsIgnoreCase(value) || "f".equalsIgnoreCase(value) || "no".equalsIgnoreCase(value)
						|| "false".equalsIgnoreCase(value)) {
					return (T) Boolean.FALSE;
				}

				throw U.rte("Cannot convert the string value '%s' to boolean!", value);

			case BYTE:
			case BYTE_OBJ:
				return (T) new Byte(value);

			case SHORT:
			case SHORT_OBJ:
				return (T) new Short(value);

			case CHAR:
			case CHAR_OBJ:
				return (T) new Character(value.charAt(0));

			case INT:
			case INT_OBJ:
				return (T) new Integer(value);

			case LONG:
			case LONG_OBJ:
				return (T) new Long(value);

			case FLOAT:
			case FLOAT_OBJ:
				return (T) new Float(value);

			case DOUBLE:
			case DOUBLE_OBJ:
				return (T) new Double(value);

			case STRING:
				return (T) value;

			case OBJECT:
				throw U.rte("Cannot convert string value to type '%s'!", toType);

			case DATE:
				return (T) Dates.date(value);

			case UUID:
				return (T) UUID.fromString(value);

			default:
				throw Err.notExpected();
		}
	}

	@SuppressWarnings("unchecked")
	public static <T> T convert(Object value, Class<T> toType) {

		if (value == null) {
			return null;
		}

		if (toType.isAssignableFrom(value.getClass())) {
			return (T) value;
		}

		if (toType.equals(Object.class)) {
			return (T) value;
		}

		if (value instanceof String) {
			return convert((String) value, toType);
		}

		TypeKind targetKind = Cls.kindOf(toType);
		boolean isNum = value instanceof Number;

		switch (targetKind) {

			case NULL:
				throw Err.notExpected();

			case BOOLEAN:
			case BOOLEAN_OBJ:
				if (value instanceof Boolean) {
					return (T) value;
				} else {
					throw U.rte("Cannot convert the value '%s' to boolean!", value);
				}

			case BYTE:
			case BYTE_OBJ:
				if (isNum) {
					return (T) new Byte(((Number) value).byteValue());
				} else {
					throw U.rte("Cannot convert the value '%s' to byte!", value);
				}

			case SHORT:
			case SHORT_OBJ:
				if (isNum) {
					return (T) new Short(((Number) value).shortValue());
				} else {
					throw U.rte("Cannot convert the value '%s' to short!", value);
				}

			case CHAR:
			case CHAR_OBJ:
				if (isNum) {
					return (T) new Character((char) ((Number) value).intValue());
				} else {
					throw U.rte("Cannot convert the value '%s' to char!", value);
				}

			case INT:
			case INT_OBJ:
				if (isNum) {
					return (T) new Integer(((Number) value).intValue());
				} else {
					throw U.rte("Cannot convert the value '%s' to int!", value);
				}

			case LONG:
			case LONG_OBJ:
				if (isNum) {
					return (T) new Long(((Number) value).longValue());
				} else {
					throw U.rte("Cannot convert the value '%s' to long!", value);
				}

			case FLOAT:
			case FLOAT_OBJ:
				if (isNum) {
					return (T) new Float(((Number) value).floatValue());
				} else {
					throw U.rte("Cannot convert the value '%s' to float!", value);
				}

			case DOUBLE:
			case DOUBLE_OBJ:
				if (isNum) {
					return (T) new Double(((Number) value).doubleValue());
				} else {
					throw U.rte("Cannot convert the value '%s' to double!", value);
				}

			case STRING:
				if (value instanceof Date) {
					return (T) Dates.str((Date) value);
				} else if (value instanceof byte[]) {
					return (T) new String((byte[]) value);
				} else if (value instanceof char[]) {
					return (T) new String((char[]) value);
				} else {
					return (T) U.str(value);
				}

			case OBJECT:
				throw U.rte("Cannot convert the value to type '%s'!", toType);

			case DATE:
				if (value instanceof Date) {
					return (T) value;
				} else if (value instanceof Number) {
					return (T) new Date(((Number) value).longValue());
				} else {
					throw U.rte("Cannot convert the value '%s' to date!", value);
				}

			default:
				throw Err.notExpected();
		}
	}

	public static Map<String, Class<?>> classMap(Iterable<Class<?>> classes) {
		Map<String, Class<?>> map = new LinkedHashMap<String, Class<?>>();

		for (Class<?> cls : classes) {
			map.put(cls.getSimpleName(), cls);
		}

		return map;
	}

	public static Class<?>[] typesOf(Object[] args) {
		Class<?>[] types = new Class<?>[args.length];

		for (int i = 0; i < types.length; i++) {
			types[i] = args[i] != null ? args[i].getClass() : null;
		}

		return types;
	}

	public static Method findMethodByArgs(Class<? extends Object> clazz, String name, Object... args) {

		for (Method method : clazz.getMethods()) {
			Class<?>[] paramTypes = method.getParameterTypes();

			if (method.getName().equals(name) && areAssignable(paramTypes, args)) {
				return method;
			}
		}

		return null;
	}

	@SuppressWarnings("unchecked")
	public static <T> Class<T> clazz(Type type) {
		return (Class<T>) (type instanceof Class ? type : Object.class);
	}

	public static Class<?> of(Object obj) {
		return obj != null ? obj.getClass() : Object.class;
	}

	public static String str(Object value) {
		return convert(value, String.class);
	}

	public static boolean bool(Object value) {
		return U.or(convert(value, Boolean.class), false);
	}

	public static ParameterizedType generic(Type type) {
		return (type instanceof ParameterizedType) ? ((ParameterizedType) type) : null;
	}

	public static boolean isJREClass(String canonicalClassName) {
		return JRE_CLASS_PATTERN.matcher(canonicalClassName).matches();
	}

	@SuppressWarnings("unchecked")
	public static <T> Class<T> getWrapperClass(Class<T> c) {
		U.must(c.isPrimitive());
		return c.isPrimitive() ? (Class<T>) PRIMITIVE_WRAPPERS.get(c) : c;
	}

	public static boolean instanceOf(Object obj, Class<?>... classes) {
		return obj != null ? isAssignableTo(obj.getClass(), classes) : false;
	}

	public static boolean isAssignableTo(Class<?> clazz, Class<?>... targetClasses) {
		for (Class<?> cls : targetClasses) {
			if (cls.isPrimitive()) {
				if (cls.isAssignableFrom(clazz)) {
					return true;
				}
				cls = getWrapperClass(cls);
			}
			if (cls.isAssignableFrom(clazz)) {
				return true;
			}
		}

		return false;
	}

	public static boolean areAssignable(Class<?>[] types, Object[] values) {
		if (types.length != values.length) {
			return false;
		}

		for (int i = 0; i < values.length; i++) {
			Object val = values[i];
			if (val != null && !instanceOf(val, types[i])) {
				return false;
			}
		}

		return true;
	}

	@SuppressWarnings("unchecked")
	public static <T> T newInstance(Class<T> clazz) {

		if (clazz == List.class) {
			return (T) U.list();
		} else if (clazz == Set.class) {
			return (T) U.set();
		} else if (clazz == Map.class) {
			return (T) U.map();
		} else if (clazz == ConcurrentMap.class) {
			return (T) Coll.concurrentMap();
		} else if (clazz == Var.class) {
			return (T) Vars.var("<new>", null);
		} else if (clazz == Object.class) {
			return (T) new Object();
		}

		return newBeanInstance(clazz);
	}

	public static <T> T newBeanInstance(Class<T> clazz) {
		try {
			Constructor<T> constr = clazz.getDeclaredConstructor();
			boolean accessible = constr.isAccessible();
			constr.setAccessible(true);

			T obj = constr.newInstance();

			constr.setAccessible(accessible);
			return obj;
		} catch (Exception e) {
			throw U.rte(e);
		}
	}


	@SuppressWarnings("unchecked")
	public static <T> T newInstance(Class<T> clazz, Object... args) {
		for (Constructor<?> constr : clazz.getConstructors()) {
			Class<?>[] paramTypes = constr.getParameterTypes();
			if (areAssignable(paramTypes, args)) {
				try {
					boolean accessible = constr.isAccessible();
					constr.setAccessible(true);

					T obj = (T) constr.newInstance(args);

					constr.setAccessible(accessible);
					return obj;
				} catch (Exception e) {
					throw U.rte(e);
				}
			}
		}

		throw U.rte("Cannot find appropriate constructor for %s with args %s!", clazz, U.str(args));
	}

	public static <T> T customizable(Class<T> clazz, Object... args) {
		String customClassName = "Customized" + clazz.getSimpleName();

		Class<T> customClass = getClassIfExists(customClassName);

		if (customClass == null) {
			customClass = getClassIfExists("custom." + customClassName);
		}

		if (customClass != null && !clazz.isAssignableFrom(customClass)) {
			customClass = null;
		}

		return newInstance(U.or(customClass, clazz), args);
	}

	@SuppressWarnings("unchecked")
	public static <T> Class<T> getClassIfExists(String className) {
		try {
			return (Class<T>) Class.forName(className);
		} catch (ClassNotFoundException e) {
			return null;
		}
	}

	public static Class<?> unproxy(Class<?> cls) {
		if (Proxy.class.isAssignableFrom(cls)) {
			for (Class<?> interf : cls.getInterfaces()) {
				if (!isJREClass(interf.getCanonicalName())) {
					return interf;
				}
			}
			throw U.rte("Cannot unproxy the class: %s!", cls);
		} else {
			return cls;
		}
	}

	public static String entityName(Class<?> cls) {
		return Cls.unproxy(cls).getSimpleName();
	}

	public static String entityName(Object entity) {
		U.notNull(entity, "entity");
		return entityName(entity.getClass());
	}

	public static boolean isSimple(Object target) {
		return kindOf(target).isSimple();
	}

	public static boolean isNumber(Object target) {
		return kindOf(target).isNumber();
	}

	public static boolean isDataStructure(Class<?> clazz) {
		return (Collection.class.isAssignableFrom(clazz)) || (Map.class.isAssignableFrom(clazz))
				|| (Object[].class.isAssignableFrom(clazz));
	}

	public static boolean isBeanType(Class<?> clazz) {
		return kindOf(clazz) == TypeKind.OBJECT
				&& !clazz.isAnnotation()
				&& !clazz.isEnum()
				&& !clazz.isInterface()
				&& !(Collection.class.isAssignableFrom(clazz))
				&& !(Map.class.isAssignableFrom(clazz))
				&& !(Object[].class.isAssignableFrom(clazz))
				&& !clazz.getPackage().getName().startsWith("java.")
				&& !clazz.getPackage().getName().startsWith("javax.");
	}

	public static boolean isBean(Object target) {
		return target != null && isBeanType(target.getClass());
	}

	public static <T, T2> T struct(Class<T> clazz1, Class<T2> clazz2, Object obj) {
		List<Object> items = U.list();

		if (obj instanceof Map<?, ?>) {
			Map<?, ?> map = U.cast(obj);

			for (Entry<?, ?> e : map.entrySet()) {
				items.add(createFromEntry(clazz2, e, null));
			}

		} else if (obj instanceof List<?>) {
			List<?> list = U.cast(obj);

			for (Object o : list) {

				if (o instanceof Map<?, ?>) {
					Map<?, ?> map = U.cast(o);

					if (!map.isEmpty()) {
						if (map.size() == 1) {
							items.add(createFromEntry(clazz2, map.entrySet().iterator().next(), null));
						} else {
							// more than 1 element
							Map<String, Object> extra = U.map();
							Iterator<Entry<Object, Object>> it = U.cast(map.entrySet().iterator());
							Entry<?, ?> firstEntry = it.next();

							while (it.hasNext()) {
								Entry<?, ?> e = U.cast(it.next());
								extra.put(Cls.str(e.getKey()), e.getValue());
							}

							items.add(createFromEntry(clazz2, firstEntry, extra));
						}
					}

				} else {
					items.add(Cls.newInstance(clazz2, Cls.str(o), null, null));
				}
			}

		} else {
			items.add(Cls.newInstance(clazz2, Cls.str(obj), null, null));
		}

		return Cls.newInstance(clazz1, items);
	}

	private static <T2> T2 createFromEntry(Class<T2> clazz2, Entry<?, ?> e, Map<String, Object> extra) {
		String key = Cls.str(e.getKey());
		T2 item = Cls.newInstance(clazz2, key, e.getValue(), extra);
		return item;
	}

	public static boolean exists(String className) {
		return getClassIfExists(className) != null;
	}

	public static Method getLambdaMethod(Serializable lambda) {
		Method writeReplace = getMethod(lambda.getClass(), "writeReplace");
		SerializedLambda serializedLambda = invoke(writeReplace, lambda);

		String className = serializedLambda.getImplClass().replaceAll("/", ".");

		Class<?> cls = getClassIfExists(className);
		U.must(cls != null, "Cannot find or load the lambda class: %s", cls);

		String lambdaMethodName = serializedLambda.getImplMethodName();

		for (Method method : cls.getDeclaredMethods()) {
			if (method.getName().equals(lambdaMethodName)) {
				return method;
			}
		}

		throw U.rte("Cannot find the lambda method!");
	}

	public static String[] getMethodParameterNames(Method method) {
		String[] names = new String[method.getParameterCount()];

		boolean defaultNames = true;
		Parameter[] parameters = method.getParameters();
		for (int i = 0; i < parameters.length; i++) {
			names[i] = parameters[i].getName();
			U.notNull(names[i], "parameter name");
			if (!names[i].equals("arg" + i)) {
				defaultNames = false;
			}
		}

		if (defaultNames) {
			CtMethod cm;
			try {
				ClassPool cp = ClassPool.getDefault();
				CtClass cc = cp.get(method.getDeclaringClass().getName());

				CtClass[] params = new CtClass[method.getParameterCount()];
				for (int i = 0; i < params.length; i++) {
					params[i] = cp.get(method.getParameterTypes()[i].getName());
				}
				cm = cc.getDeclaredMethod(method.getName(), params);
			} catch (NotFoundException e) {
				throw U.rte("Cannot find the target method!", e);
			}

			MethodInfo methodInfo = cm.getMethodInfo();
			CodeAttribute codeAttribute = methodInfo.getCodeAttribute();
			LocalVariableAttribute attr = (LocalVariableAttribute) codeAttribute.getAttribute(LocalVariableAttribute.tag);

			int offset = javassist.Modifier.isStatic(cm.getModifiers()) ? 0 : 1;

			for (int i = 0; i < attr.tableLength(); i++) {
				int index = i - offset;
				String var = attr.variableName(i);

				if (index >= 0 && index < names.length) {
					names[index] = var;
				}
			}
		}

		return names;
	}

}
