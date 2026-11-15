package com.sacco.account.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

@Embeddable
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class AccountHolderId implements Serializable {
    @Column(name = "account_id")
    private Long accountId;

    @Column(name = "member_id")
    private Long memberId;
}
