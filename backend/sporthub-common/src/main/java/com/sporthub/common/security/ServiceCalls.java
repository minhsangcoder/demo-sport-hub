package com.sporthub.common.security;

import com.sporthub.common.exception.ForbiddenException;
import java.time.Instant;

/** Technical authentication for private service calls. Business commands remain in their owning service. */
public final class ServiceCalls {
    private ServiceCalls() {}
    public static String sign(String secret,String method,String path,long timestamp,String body) {
        return Signatures.hmac(secret,"SERVICE|"+method+"|"+path+"|"+timestamp+"|"+Signatures.sha256(body));
    }
    public static void verify(String secret,String method,String path,String timestamp,String body,String signature) {
        try {
            long time=Long.parseLong(timestamp);
            long now=Instant.now().getEpochSecond();
            if(secret.length()<32||time<now-300||time>now+300||
                !Signatures.matches(sign(secret,method,path,time,body),signature))throw new IllegalArgumentException();
        } catch(Exception ex) {throw new ForbiddenException("Invalid private service signature");}
    }
}
