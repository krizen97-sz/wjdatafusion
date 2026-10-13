#include <arpa/inet.h>
#include <errno.h>
#include <stdio.h>
#include <sys/socket.h>
#include <unistd.h>
int main(void) {
    struct sockaddr_in a = {.sin_len=sizeof(a),.sin_family=AF_INET,.sin_port=htons(HDI_TEST_PORT),.sin_addr.s_addr=htonl(INADDR_ANY)};
    int s=socket(AF_INET,SOCK_STREAM,0); if(bind(s,(struct sockaddr*)&a,sizeof(a)) || listen(s,4)) {perror("listen");return 2;}
    struct sockaddr_in other=a;other.sin_port=htons(HDI_DENIED_PORT);
    int d=socket(AF_INET,SOCK_STREAM,0);int denied=bind(d,(struct sockaddr*)&other,sizeof(other));close(d);
    printf("READY unauthorizedPortDenied=%d\n",denied<0);fflush(stdout);
    for(;;){int c=accept(s,0,0);if(c>=0){write(c,"owned-loopback-proof",20);close(c);}}
}
