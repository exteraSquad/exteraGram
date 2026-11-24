# This is the source code of the exteraGram plugins SDK.
#
# We do not and cannot prevent the use of our code,
# but be respectful and credit the original author.
#
# Copyright @exteraGram, 2022-2026
#
# Licensed under the GNU GPL v. 3 or later, with the additional terms
# in ADDITIONAL_TERMS.md at the root of the repository.

import _sdk_version
_sdk_version.Z.c()

import random
import string
from dataclasses import dataclass, field
from typing import List, Callable, Any, Optional

from android.view import View
from org.telegram.ui.Components import UItem
from com.exteragram.messenger.plugins import PluginsConstants
from com.exteragram.messenger.plugins.models import CustomSetting

from extera_utils.classes import Base, java_subclass, joverride, jfield, jgetmethod, jclassbuilder, PyObj, jsetmethod
from com.android.dx import TypeId, Label, Comparison
from java import jarray
from java.lang.reflect import Modifier
from java.lang import Object
from com.chaquo.python import PyObject
from com.exteragram.messenger.plugins.utils import ClassProxy as CP


@dataclass
class Switch:
    key: str
    text: str
    default: bool
    subtext: str = None
    icon: str = None
    on_change: Callable[[bool], None] = field(default=None, compare=False, repr=False)
    type: str = field(default=PluginsConstants.Settings.TYPE_SWITCH, init=False)
    on_long_click: Callable[[View], None] = field(default=None, compare=False, repr=False)
    link_alias: str = None


@dataclass
class Selector:
    key: str
    text: str
    default: int
    items: List[str]
    icon: str = None
    on_change: Callable[[int], None] = field(default=None, compare=False, repr=False)
    type: str = field(default=PluginsConstants.Settings.TYPE_SELECTOR, init=False)
    on_long_click: Callable[[View], None] = field(default=None, compare=False, repr=False)
    link_alias: str = None


@dataclass
class Input:
    key: str
    text: str
    default: str = ""
    subtext: str = None
    icon: str = None
    on_change: Callable[[str], None] = field(default=None, compare=False, repr=False)
    type: str = field(default=PluginsConstants.Settings.TYPE_INPUT, init=False)
    on_long_click: Callable[[View], None] = field(default=None, compare=False, repr=False)
    link_alias: str = None


@dataclass
class Text:
    text: str
    subtext: str = None
    icon: str = None
    accent: bool = False
    red: bool = False
    on_click: Callable[[View], None] = field(default=None, compare=False, repr=False)
    create_sub_fragment: Callable[[], List[Any]] = field(default=None, compare=False, repr=False)
    type: str = field(default=PluginsConstants.Settings.TYPE_TEXT, init=False)
    on_long_click: Callable[[View], None] = field(default=None, compare=False, repr=False)
    link_alias: str = None


@dataclass
class Header:
    text: str
    type: str = field(default=PluginsConstants.Settings.TYPE_HEADER, init=False)


@dataclass
class Divider:
    text: str = None
    type: str = field(default=PluginsConstants.Settings.TYPE_DIVIDER, init=False)


@dataclass
class EditText:
    key: str
    hint: str
    default: str = ""
    multiline: bool = False
    max_length: int = 256
    mask: str = None
    on_change: Callable[[str], None] = field(default=None, compare=False, repr=False)
    type: str = field(default=PluginsConstants.Settings.TYPE_EDIT_TEXT, init=False)


@dataclass
class Custom:
    type: str = field(default=PluginsConstants.Settings.TYPE_CUSTOM, init=False)
    item: UItem = field(default=None, compare=False, repr=False)
    view: View = field(default=None, compare=False, repr=False)
    factory: CustomSetting.Factory = field(default=None, compare=False, repr=False)
    factory_args: Any = field(default=None, compare=False, repr=False)
    on_click: Callable[[View], None] = field(default=None, compare=False, repr=False)
    on_long_click: Callable[[View], None] = field(default=None, compare=False, repr=False)
    create_sub_fragment: Callable[[], List[Any]] = field(default=None, compare=False, repr=False)
    link_alias: str = None

    def __post_init__(self):
        if self.item is None and self.view is None and self.factory is None:
            raise ValueError("Either item or view or factory must be provided")

        if self.item and not isinstance(self.item, UItem):
            raise ValueError(f"item must be an instance of UItem, but got {self.item.__class__.__name__}")

        if self.view and not isinstance(self.view, View):
            raise ValueError(f"view must be an instance of View, but got {self.view.__class__.__name__}")

        java_factory = getattr(self.factory, "java", self.factory)
        if self.factory and not isinstance(java_factory, CustomSetting.Factory):
            raise ValueError(f"factory must be an instance of CustomSetting.Factory, but got {self.factory.__class__.__name__}")

pyobject_type = TypeId.get(PyObject.getClass())
object_array_type = TypeId.get(jarray(Object))
typehelper = TypeId.get(CP.TypeHelper.getClass())
pyobject_call_method = pyobject_type.getMethod(pyobject_type, "call", object_array_type)

def _gen_simple_custom_setting_factory_class():
    @java_subclass(CustomSetting.Factory, custom_name="".join(random.choices(string.ascii_letters, k=10)))
    class _CustomSettingFactoryProxy(Base):
        __jowner_from_caller__ = True

        attached_view_handler = jfield(PyObject.getClass(), default=None, methods=[jgetmethod("getAttachedViewHandler"), jsetmethod("setAttachedViewHandler")])
        equals_handler = jfield(PyObject.getClass(), default=None, methods=[jgetmethod("getEqualsHandler"), jsetmethod("setEqualsHandler")])
        content_equals_handler = jfield(PyObject.getClass(), default=None, methods=[jgetmethod("getContentEqualsHandler"), jsetmethod("setContentEqualsHandler")])
        on_click_handler = jfield(PyObject.getClass(), default=None, methods=[jgetmethod("getOnClickHandler"), jsetmethod("setOnClickHandler")])
        on_long_click_handler = jfield(PyObject.getClass(), default=None, methods=[jgetmethod("getOnLongClickHandler"), jsetmethod("setOnLongClickHandler")])

        def __init__(self, create_item_fn, create_view_fn, bind_view_fn):
            self.create_item_fn = create_item_fn
            self.create_view_fn = create_view_fn
            self.bind_view_fn = bind_view_fn

        @joverride("create")
        def create(self, plugin, setting, args):
            if not self.create_item_fn:
                item = UItem(self.java.viewType, False)
                item.object = args
                return item
            
            return self.create_item_fn(plugin, setting, args)

        def on_release(self):
            for name in _HANDLER_FIELDS:
                setattr(self, name, None)
            self.create_item_fn = self.create_view_fn = self.bind_view_fn = None
            _unregister_factory(self.java)

        @joverride("createView")
        def createView(self, context, list_view, current_account: int, class_guid: int, resources_provider):
            return self.create_view_fn(context, list_view, current_account, class_guid, resources_provider)

        @joverride("bindView")
        def bindView(self, view, item, divider: bool, adapter, list_view):
            self.bind_view_fn(view, item, divider, adapter, list_view)

        @staticmethod
        def _gen_method(dex, proxy_type, method, field_name):
            field_id = proxy_type.getField(pyobject_type, field_name)

            params = list(method.getParameterTypes())
            param_types = [TypeId.get(param) for param in params]

            return_type = TypeId.get(method.getReturnType())
            method_name = method.getName()

            is_void = return_type == TypeId.VOID
            is_boolean = return_type == TypeId.BOOLEAN

            method_id = proxy_type.getMethod(return_type, method_name, jarray(TypeId)(param_types))
            code = dex.declare(method_id, Modifier.PUBLIC)

            this_ref = code.getThis(proxy_type)
            null_ref = code.newLocal(TypeId.OBJECT)
            handler = code.newLocal(pyobject_type)
            py_call_result = None if is_void else code.newLocal(pyobject_type)
            result = None if is_void else code.newLocal(return_type)
            args_array = code.newLocal(object_array_type)
            size = code.newLocal(TypeId.INT)
            tmp_idx = code.newLocal(TypeId.INT)

            param_locals = [code.getParameter(i, param_types[i]) for i in range(len(param_types))]
            boxed_locals = [code.newLocal(TypeId.OBJECT) for _ in range(len(param_types))]

            code.loadConstant(null_ref, None)
            code.loadConstant(size, len(param_types))
            code.newArray(args_array, size)
            code.iget(field_id, handler, this_ref)

            no_handler = Label()
            code.compare(Comparison.EQ, no_handler, handler, null_ref)

            for i in range(len(param_types)):
                box_method = typehelper.getMethod(TypeId.OBJECT, "box", param_types[i] if params[i].isPrimitive() else TypeId.OBJECT)
                code.invokeStatic(box_method, boxed_locals[i], param_locals[i])

                code.loadConstant(tmp_idx, i)
                code.aput(args_array, tmp_idx, boxed_locals[i])
            
            if is_void:
                code.invokeVirtual(pyobject_call_method, None, handler, args_array)
                code.returnVoid()
            elif is_boolean:
                code.invokeVirtual(pyobject_call_method, py_call_result, handler, args_array)

                to_boolean = pyobject_type.getMethod(TypeId.BOOLEAN, "toBoolean")
                code.invokeVirtual(to_boolean, result, py_call_result)
                code.returnValue(result)
            else:
                raise RuntimeError(f"{method_name}: method return type must be only void/bool")

            code.mark(no_handler)

            if field_name in ["equals_handler", "content_equals_handler"]:
                uitem_type = TypeId.get(UItem.getClass())
                m = uitem_type.getMethod(
                    TypeId.BOOLEAN,
                    "itemEquals" if field_name == "equals_handler" else "itemContentEquals",
                    uitem_type
                )
                code.invokeVirtual(m, result, param_locals[0], param_locals[1])
                code.returnValue(result)
                return

            if is_void:
                code.returnVoid()
            elif is_boolean:
                code.loadConstant(result, False)
                code.returnValue(result)

        @jclassbuilder()
        @staticmethod
        def create_methods(dex, proxy_type, _, __):
            methods = {
                "attachedView": "attached_view_handler",
                "equals": "equals_handler",
                "contentEquals": "content_equals_handler",
                "onClick": "on_click_handler",
                "onLongClick": "on_long_click_handler"
            }
            for m in [
                method
                for method in list(UItem.UItemFactory.getClass().getDeclaredMethods()) + list(CustomSetting.Factory.getClass().getDeclaredMethods())
                if method.getName() in methods.keys()
            ]:
                _CustomSettingFactoryProxy._gen_method(dex, proxy_type, m, methods.pop(m.getName()))
    return _CustomSettingFactoryProxy


_HANDLER_FIELDS = ("attached_view_handler", "equals_handler", "content_equals_handler", "on_click_handler",
                   "on_long_click_handler")
_factory_class = None


def _simple_custom_setting_factory_class():
    global _factory_class
    if _factory_class is None:
        _factory_class = _gen_simple_custom_setting_factory_class()
    return _factory_class


def _uitem_registry(name):
    field = UItem.getClass().getDeclaredField(name)
    field.setAccessible(True)
    return field.get(None)


def _register_factory(java_factory):
    UItem.UItemFactory.setup(java_factory)
    if UItem.findFactory(java_factory.viewType) is None:
        _uitem_registry("factories").put(java_factory.viewType, java_factory)


def _unregister_factory(java_factory):
    if java_factory is None:
        return
    factories = _uitem_registry("factories")
    registered = factories.get(java_factory.viewType) if factories is not None else None
    if registered is not None and registered.equals(java_factory):
        factories.remove(java_factory.viewType)
    instances = _uitem_registry("factoryInstances")
    registered = instances.get(java_factory.getClass()) if instances is not None else None
    if registered is not None and registered.equals(java_factory):
        instances.remove(java_factory.getClass())


class SimpleSettingFactory:
    def __init__(
        self,
        create_view: callable,
        bind_view: callable,
        *,
        is_clickable: bool = False,
        is_shadow: bool = False,
        create_item: Optional[callable] = None,
        on_click: Optional[callable] = None,
        on_long_click: Optional[callable] = None,
        attached_view: Optional[callable] = None,
        equals: Optional[callable] = None,
        content_equals: Optional[callable] = None,
    ):
        self.create_view = create_view
        self.bind_view = bind_view
        self.is_clickable = is_clickable
        self.is_shadow = is_shadow
        self.create_item = create_item
        self.on_click = on_click
        self.on_long_click = on_long_click
        self.attached_view = attached_view
        self.equals = equals
        self.content_equals = content_equals
        
        self.instance = _simple_custom_setting_factory_class().new_instance(init_args=[create_item, create_view, bind_view])
        if attached_view:
            self.instance.setAttachedViewHandler(attached_view)
        if equals:
            self.instance.setEqualsHandler(equals)
        if content_equals:
            self.instance.setContentEqualsHandler(content_equals)
        if on_click:
            self.instance.setOnClickHandler(on_click)
        if on_long_click:
            self.instance.setOnLongClickHandler(on_long_click)

        _register_factory(self.instance.java)

        self.instance.java.isShadowValue = is_shadow
        self.instance.java.isClickableValue = is_clickable

    def release(self):
        return self.instance.jrelease()
    
    def __call__(self, *args, create_sub_fragment=None, link_alias=None):
        return Custom(
            factory=self.instance.java,
            factory_args=PyObj.create(args) if args else None,
            link_alias=link_alias,
            create_sub_fragment=create_sub_fragment
        )
