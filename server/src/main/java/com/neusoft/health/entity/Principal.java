package com.neusoft.health.entity;

import java.io.Serializable;

public record Principal(String userId, Long docId, String role, Long hpId)
    implements Serializable {}
