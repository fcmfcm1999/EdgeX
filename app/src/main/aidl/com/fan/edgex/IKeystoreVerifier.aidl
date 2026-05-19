package com.fan.edgex;

interface IKeystoreVerifier {
    byte[] sign(in byte[] challenge);
}
