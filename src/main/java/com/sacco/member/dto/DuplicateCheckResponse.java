package com.sacco.member.dto;

import com.sacco.member.entity.Member;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DuplicateCheckResponse {

    private boolean hasPotentialDuplicate;
    private List<Member> potentialMatches;

    public static DuplicateCheckResponse fromEntity(List<Member> matches) {
        return DuplicateCheckResponse.builder()
                .hasPotentialDuplicate(!matches.isEmpty())
                .potentialMatches(matches)
                .build();
    }
}
