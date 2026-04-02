package com.justjava.mycommunity.organization;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class Channel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String description;



//    @ManyToOne(fetch = FetchType.EAGER)
//    @JoinColumn(name = "ORG_ID")
//    private Organization organization;
}
