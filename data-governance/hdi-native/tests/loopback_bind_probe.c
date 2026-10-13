#include <sys/socket.h>
#include <netinet/in.h>
#include <string.h>
#include <stdint.h>
#ifndef HDI_DATA_PORT
#error "Explicit HDI_DATA_PORT, HDI_RPC_PORT and HDI_TEST_PORT are required"
#endif
/* Mac test-only network shim. Keep vendor bytecode, TLS and business algorithms unchanged. */
static int allowed_test_port(unsigned short port) {
    return port == HDI_DATA_PORT || port == HDI_RPC_PORT || port == HDI_TEST_PORT;
}
static int probe_loopback_bind(int fd, const struct sockaddr *address, socklen_t length) {
    struct sockaddr_storage copy;
    if (address && length <= sizeof(copy)) {
        memcpy(&copy, address, length);
        if (address->sa_family == AF_INET && length >= sizeof(struct sockaddr_in)) {
            struct sockaddr_in *v4 = (struct sockaddr_in *)&copy;
            if (allowed_test_port(ntohs(v4->sin_port)) && v4->sin_addr.s_addr == htonl(INADDR_ANY)) {
                v4->sin_addr.s_addr = htonl(INADDR_LOOPBACK);
                return bind(fd, (const struct sockaddr *)v4, length);
            }
        } else if (address->sa_family == AF_INET6 && length >= sizeof(struct sockaddr_in6)) {
            struct sockaddr_in6 *v6 = (struct sockaddr_in6 *)&copy;
            if (allowed_test_port(ntohs(v6->sin6_port)) && IN6_IS_ADDR_UNSPECIFIED(&v6->sin6_addr)) {
                v6->sin6_addr = in6addr_loopback;
                return bind(fd, (const struct sockaddr *)v6, length);
            }
            if (allowed_test_port(ntohs(v6->sin6_port)) && IN6_IS_ADDR_V4MAPPED(&v6->sin6_addr)) {
                uint32_t tail; memcpy(&tail, &v6->sin6_addr.s6_addr[12], sizeof(tail));
                if (tail == 0) {
                    tail = htonl(INADDR_LOOPBACK);
                    memcpy(&v6->sin6_addr.s6_addr[12], &tail, sizeof(tail));
                    return bind(fd, (const struct sockaddr *)v6, length);
                }
            }
        }
    }
    return bind(fd, address, length);
}
__attribute__((used)) static struct { const void *replacement; const void *replacee; } binding
__attribute__((section("__DATA,__interpose"))) = { (const void *)(uintptr_t)&probe_loopback_bind, (const void *)(uintptr_t)&bind };
