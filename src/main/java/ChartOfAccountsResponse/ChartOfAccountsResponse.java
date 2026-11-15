/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.sacco.ledger.dto;

public record ChartOfAccountsResponse(
        String code,
        String name,
        String category,
        boolean bank,
        boolean cash,
        boolean active
) {}
