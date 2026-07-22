"""End-to-end verification of the Python package produced by j2pyi.

Run by the ``graalPyIntegrationTest`` Gradle task. The task puts the generated
module root on ``PYTHONPATH`` and passes the compiled Java example classes to
GraalPy's JVM classpath.
"""

from com.example import Bounded, Box, Hello, UseSite


def main() -> None:
    # Importing from the generated package proves that its runtime __init__.py
    # can resolve every exported Java type through GraalPy's java.type().
    assert str(Hello().greet("GraalPy")) == "Hello, GraalPy!"

    # Exercise both generated constructor overloads and an instance method.
    empty_box = Box()
    empty_box.set("updated")
    assert str(empty_box.get()) == "updated"
    assert str(Box("initial").get()) == "initial"

    # A Python number crosses the interop boundary into a bounded Java generic.
    assert Bounded().id(7) == 7

    # Calls on the remaining exported class prove its binding is usable too.
    use_site = UseSite()
    assert use_site.anyList() is None
    assert use_site.mapWild() is None


if __name__ == "__main__":
    main()
