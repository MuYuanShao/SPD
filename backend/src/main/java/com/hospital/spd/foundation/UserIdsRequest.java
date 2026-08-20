package com.hospital.spd.foundation;

import java.util.List;

public record UserIdsRequest(
        List<Long> userIds
) {
}
