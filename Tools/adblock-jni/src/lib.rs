use std::collections::HashSet;

use adblock::resources::{MimeType, PermissionMask, Resource, ResourceType};
use adblock::{
    Engine,
    lists::{FilterSet, ParseOptions},
    request::Request,
};
use jni::JNIEnv;
use jni::objects::{JClass, JObject, JObjectArray, JString, JValue};
use jni::sys::{jboolean, jlong, jobject, jsize};

use std::fs::File;
use std::io::{BufRead, BufReader};

#[derive(Default)]
#[repr(C)]
pub struct BlockResult {
    matched: jboolean,
    important: jboolean,
    redirect: Option<String>,
    rewritten_url: Option<String>,
    exception: Option<String>,
    filter: Option<String>,
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_exteragram_messenger_adblock_interop_NativeAdBlock_createFilterSet(
    mut env: JNIEnv,
    _: JClass,
    filters_array: JObjectArray,
) -> jlong {
    let length = env.get_array_length(&filters_array).unwrap_or(0) as usize;
    let mut rules: Vec<String> = Vec::with_capacity(length);

    for i in 0..length {
        if let Ok(filter_obj) = env.get_object_array_element(&filters_array, i as i32) {
            if let Ok(jstring) = env.get_string(&JString::from(filter_obj)) {
                rules.push(jstring.to_string_lossy().into_owned());
            }
        }
    }

    let mut filter_set = FilterSet::new(false);
    filter_set.add_filters(&rules, ParseOptions::default());

    Box::into_raw(Box::new(filter_set)) as jlong
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_exteragram_messenger_adblock_interop_NativeAdBlock_addFilters(
    mut env: JNIEnv,
    _: JClass,
    filter_set_ptr: jlong,
    filters_path: JString,
) -> jobject {
    let filter_set = unsafe { &mut *(filter_set_ptr as *mut FilterSet) };

    let filepath = env
        .get_string(&filters_path)
        .expect("Couldn't get filepath string")
        .to_string_lossy()
        .into_owned();

    let mut rules: Vec<String> = Vec::new();

    if let Ok(file) = File::open(&filepath) {
        let reader = BufReader::new(file);

        for line in reader.lines() {
            if let Ok(rule) = line {
                let trimmed = rule.trim();

                if !trimmed.is_empty() {
                    if !trimmed.starts_with('!')
                        || trimmed.starts_with("! Title:")
                        || trimmed.starts_with("! Expires:")
                        || trimmed.starts_with("! Homepage:")
                        || trimmed.starts_with("! Redirect:")
                    {
                        rules.push(trimmed.to_owned());
                    }
                }
            }
        }
    } else {
        return std::ptr::null_mut();
    }

    let metadata = filter_set.add_filters(&rules, ParseOptions::default());

    let homepage = env
        .new_string(metadata.homepage.unwrap_or_default())
        .unwrap();
    let title = env.new_string(metadata.title.unwrap_or_default()).unwrap();
    let redirect = env
        .new_string(metadata.redirect.unwrap_or_default())
        .unwrap();
    let mut expires: i32 = 0;

    match metadata.expires {
        Some(interval) => match interval {
            adblock::lists::ExpiresInterval::Hours(hours) => {
                expires = (hours as i32) * 60;
            }
            adblock::lists::ExpiresInterval::Days(days) => {
                expires = (days as i32) * 24 * 60;
            }
        },
        None => {}
    }

    let metadata_class = env
        .find_class("com/exteragram/messenger/adblock/data/FilterListMetadata")
        .unwrap();
    let metadata_obj = env
        .new_object(
            metadata_class,
            "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;I)V",
            &[
                JValue::Object(&homepage),
                JValue::Object(&title),
                JValue::Object(&redirect),
                JValue::Int(expires),
            ],
        )
        .expect("Failed to create FilterListMetadata object");

    metadata_obj.into_raw()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_exteragram_messenger_adblock_interop_NativeAdBlock_destroyFilterSet(
    _env: JNIEnv,
    _: JClass,
    filter_set_ptr: jlong,
) {
    unsafe {
        drop(Box::from_raw(filter_set_ptr as *mut FilterSet));
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_exteragram_messenger_adblock_interop_NativeAdBlock_useResources(
    mut env: JNIEnv,
    _: JClass,
    engine_ptr: jlong,
    names: JObjectArray,
    aliases_array: JObjectArray,
    extensions: JObjectArray,
    contents: JObjectArray,
) {
    let engine = unsafe { &mut *(engine_ptr as *mut Engine) };

    let count = env.get_array_length(&names).unwrap_or(0) as usize;
    let mut resources: Vec<Resource> = Vec::with_capacity(count);

    for i in 0..count {
        let name_obj = env.get_object_array_element(&names, i as i32);
        let ext_obj = env.get_object_array_element(&extensions, i as i32);
        let content_obj = env.get_object_array_element(&contents, i as i32);
        let aliases_obj = env.get_object_array_element(&aliases_array, i as i32);

        if let (Ok(name_obj), Ok(ext_obj), Ok(content_obj), Ok(aliases_obj)) =
            (name_obj, ext_obj, content_obj, aliases_obj)
        {
            let name_val = env
                .get_string(&JString::from(name_obj))
                .map(|s| s.to_string_lossy().into_owned())
                .unwrap_or_default();

            let ext_val = env
                .get_string(&JString::from(ext_obj))
                .map(|s| s.to_string_lossy().into_owned())
                .unwrap_or_default();

            let content_val = env
                .get_string(&JString::from(content_obj))
                .map(|s| s.to_string_lossy().into_owned())
                .unwrap_or_default();

            let aliases_arr = JObjectArray::from(aliases_obj);
            let aliases_len = env.get_array_length(&aliases_arr).unwrap_or(0) as usize;
            let mut aliases_val: Vec<String> = Vec::with_capacity(aliases_len);

            for j in 0..aliases_len {
                if let Ok(alias_obj) = env.get_object_array_element(&aliases_arr, j as i32) {
                    if let Ok(jstring) = env.get_string(&JString::from(alias_obj)) {
                        aliases_val.push(jstring.to_string_lossy().into_owned());
                    }
                }
            }

            resources.push(Resource {
                name: name_val,
                aliases: aliases_val,
                kind: ResourceType::Mime(MimeType::from_extension(&ext_val)),
                content: content_val,
                dependencies: Vec::new(),
                permission: PermissionMask::default(),
            });
        }
    }

    engine.use_resources(resources);
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_exteragram_messenger_adblock_interop_NativeAdBlock_getCosmeticResources(
    mut env: JNIEnv,
    _: JClass,
    engine_ptr: jlong,
    url: JString,
) -> jobject {
    let engine = unsafe { &mut *(engine_ptr as *mut Engine) };

    let url_val = env
        .get_string(&url)
        .expect("Couldn't get url string")
        .to_string_lossy()
        .into_owned();

    let result = engine.url_cosmetic_resources(&url_val);

    let injected_script = env.new_string(result.injected_script).unwrap();

    let string_class = env
        .find_class("java/lang/String")
        .expect("Couldn't find String");
    let hide_selectors = env
        .new_object_array(
            result.hide_selectors.len() as jsize,
            &string_class,
            JObject::null(),
        )
        .expect("Couldn't create hideSelectors array");
    for (i, rust_str) in result.hide_selectors.iter().enumerate() {
        let j_string = env.new_string(rust_str).unwrap();
        env.set_object_array_element(&hide_selectors, i as jsize, j_string)
            .unwrap();
    }

    let procedural_actions = env
        .new_object_array(
            result.procedural_actions.len() as jsize,
            &string_class,
            JObject::null(),
        )
        .expect("Couldn't create proceduralActions array");
    for (i, rust_str) in result.procedural_actions.iter().enumerate() {
        let j_string = env.new_string(rust_str).unwrap();
        env.set_object_array_element(&procedural_actions, i as jsize, j_string)
            .unwrap();
    }

    let exceptions = env
        .new_object_array(
            result.exceptions.len() as jsize,
            &string_class,
            JObject::null(),
        )
        .expect("Couldn't create exceptions array");
    for (i, rust_str) in result.exceptions.iter().enumerate() {
        let j_string = env.new_string(rust_str).unwrap();
        env.set_object_array_element(&exceptions, i as jsize, j_string)
            .unwrap();
    }

    let class = env
        .find_class("com/exteragram/messenger/adblock/data/UrlCosmeticResources")
        .expect("Failed to find UrlCosmeticResources class");

    let cosmetic = env
        .new_object(
            class,
            "([Ljava/lang/String;[Ljava/lang/String;[Ljava/lang/String;Ljava/lang/String;Z)V",
            &[
                JValue::Object(&hide_selectors),
                JValue::Object(&procedural_actions),
                JValue::Object(&exceptions),
                JValue::Object(&injected_script),
                JValue::Bool(if result.generichide { 1 } else { 0 }),
            ],
        )
        .expect("Failed to create UrlCosmeticResources object");

    return cosmetic.into_raw();
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_exteragram_messenger_adblock_interop_NativeAdBlock_getHiddenSelectors(
    mut env: JNIEnv,
    _: JClass,
    engine_ptr: jlong,
    classes_array: JObjectArray,
    ids_array: JObjectArray,
    exceptions_array: JObjectArray,
) -> jobject {
    let engine = unsafe { &mut *(engine_ptr as *mut Engine) };

    let classes_len = env.get_array_length(&classes_array).unwrap_or(0) as usize;
    let mut classes: Vec<String> = Vec::with_capacity(classes_len);
    for i in 0..classes_len {
        if let Ok(class_obj) = env.get_object_array_element(&classes_array, i as i32) {
            if let Ok(jstring) = env.get_string(&JString::from(class_obj)) {
                classes.push(jstring.to_string_lossy().into_owned());
            }
        }
    }

    let ids_len = env.get_array_length(&ids_array).unwrap_or(0) as usize;
    let mut ids: Vec<String> = Vec::with_capacity(ids_len);
    for i in 0..ids_len {
        if let Ok(id_obj) = env.get_object_array_element(&ids_array, i as i32) {
            if let Ok(jstring) = env.get_string(&JString::from(id_obj)) {
                ids.push(jstring.to_string_lossy().into_owned());
            }
        }
    }

    let exceptions_len = env.get_array_length(&exceptions_array).unwrap_or(0) as usize;
    let mut exceptions = HashSet::new();
    for i in 0..exceptions_len {
        if let Ok(exception_obj) = env.get_object_array_element(&exceptions_array, i as i32) {
            if let Ok(jstring) = env.get_string(&JString::from(exception_obj)) {
                exceptions.insert(jstring.to_string_lossy().into_owned());
            }
        }
    }

    let selectors = engine.hidden_class_id_selectors(classes, ids, &exceptions);

    let string_class = env
        .find_class("java/lang/String")
        .expect("Couldn't find String");
    let result_array = env
        .new_object_array(selectors.len() as jsize, string_class, JObject::null())
        .expect("Couldn't create result array");

    for (i, selector) in selectors.iter().enumerate() {
        let j_string = env.new_string(selector).unwrap();
        env.set_object_array_element(&result_array, i as jsize, j_string)
            .unwrap();
    }

    result_array.into_raw()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_exteragram_messenger_adblock_interop_NativeAdBlock_createEngine(
    _env: JNIEnv,
    _: JClass,
    filter_set_ptr: jlong,
) -> jlong {
    let filter_set = unsafe { Box::from_raw(filter_set_ptr as *mut FilterSet) };
    let engine = Engine::from_filter_set(*filter_set, true);
    Box::into_raw(Box::new(engine)) as jlong
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_exteragram_messenger_adblock_interop_NativeAdBlock_shouldBlock(
    mut env: JNIEnv,
    _: JClass,
    engine_ptr: jlong,
    url: JString,
    source_url: JString,
    resource_type: JString,
) -> jobject {
    let engine = unsafe { &*(engine_ptr as *mut Engine) };

    let url = env
        .get_string(&url)
        .expect("Couldn't get url string")
        .to_string_lossy()
        .into_owned();

    let source_url = env
        .get_string(&source_url)
        .expect("Couldn't get source_url string")
        .to_string_lossy()
        .into_owned();

    let resource_type = env
        .get_string(&resource_type)
        .expect("Couldn't get resource_type string")
        .to_string_lossy()
        .into_owned();

    let class = env
        .find_class("com/exteragram/messenger/adblock/data/BlockResult")
        .expect("Failed to find BlockResult class");

    if let Ok(request) = Request::new(&url, &source_url, &resource_type) {
        let result = engine.check_network_request(&request);

        let redirect = env.new_string(result.redirect.unwrap_or_default()).unwrap();
        let rewritten_url = env
            .new_string(result.rewritten_url.unwrap_or_default())
            .unwrap();
        let exception = env
            .new_string(result.exception.unwrap_or_default())
            .unwrap();
        let filter = env.new_string(result.filter.unwrap_or_default()).unwrap();

        let block_result = env
            .new_object(
                class,
                "(ZZLjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V",
                &[
                    JValue::Bool(if result.matched { 1 } else { 0 }),
                    JValue::Bool(if result.important { 1 } else { 0 }),
                    JValue::Object(&redirect),
                    JValue::Object(&rewritten_url),
                    JValue::Object(&exception),
                    JValue::Object(&filter),
                ],
            )
            .expect("Failed to create BlockResult object");

        return block_result.into_raw();
    }

    std::ptr::null_mut()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_exteragram_messenger_adblock_interop_NativeAdBlock_destroyEngine(
    _env: JNIEnv,
    _: JClass,
    engine_ptr: jlong,
) {
    unsafe {
        drop(Box::from_raw(engine_ptr as *mut Engine));
    }
}
