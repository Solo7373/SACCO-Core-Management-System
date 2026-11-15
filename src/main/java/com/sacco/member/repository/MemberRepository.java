package com.sacco.member.repository;

import com.sacco.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByMembershipNo(String membershipNo);
    List<Member> findByStatus(Member.MemberStatus status);
    List<Member> findByIsDeletedFalse();
    List<Member> findByIsDeletedTrue();
    long countByIsDeletedFalse();
    @Query("SELECT m FROM Member m WHERE m.phone = :phone AND m.status != 'CLOSED'")
    List<Member> findActiveByPhone(@Param("phone") String phone);
    @Query("SELECT m FROM Member m WHERE m.lastName = :lastName AND m.firstName = :firstName AND m.status != 'CLOSED'")
    List<Member> findByNameMatch(@Param("firstName") String firstName, @Param("lastName") String lastName);
    @Query("SELECT m FROM Member m WHERE m.phone = :phone OR (m.lastName = :lastName AND m.firstName = :firstName)")
    List<Member> findPotentialDuplicates(@Param("firstName") String firstName, @Param("lastName") String lastName, @Param("phone") String phone);
    boolean existsByMembershipNo(String membershipNo);
}
