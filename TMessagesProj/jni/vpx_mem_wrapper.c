#include <stdlib.h>
#include <string.h>

void* __real_vpx_malloc(size_t size);
void* __real_vpx_memalign(size_t align, size_t size);
void* __real_vpx_calloc(size_t num, size_t size);
void __real_vpx_free(void* ptr);

// AnimatedFileDrawable.java
// metaData[0] > 3840 || metaData[1] > 3840
// 3840 * 3840 * 4 = 58982400; round up to 60MB
#define MALLOC_LIMIT 60 * 1024 * 1024

void* __wrap_vpx_malloc(size_t size) {
    if (size > MALLOC_LIMIT) {
        return NULL;
    }
    return __real_vpx_malloc(size);
}

void* __wrap_vpx_memalign(size_t align, size_t size) {
    if (size + align - 1 + sizeof(size_t) > MALLOC_LIMIT) { // rough estimate
        return NULL;
    }
    return __real_vpx_memalign(align, size);
}

void* __wrap_vpx_calloc(size_t num, size_t size) {
    if (num * size > MALLOC_LIMIT) {
        return NULL;
    }
    return __real_vpx_calloc(num, size);
}

void __wrap_vpx_free(void* ptr) {
    __real_vpx_free(ptr);
}
