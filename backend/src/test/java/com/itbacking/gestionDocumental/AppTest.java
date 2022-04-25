package com.itbacking.gestionDocumental;


import com.itbacking.core.App;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;

import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;


@EnableScheduling
@EntityScan(
        basePackages = {"com.itbacking"},
        basePackageClasses = { AppTest.class}
        )
@SpringBootApplication(exclude = MongoAutoConfiguration.class) //Necesario para evitar el Autowiring de Mongo que hace sin que se pida Autowiring
//@SpringBootApplication()
public class AppTest {

    public static void main(String[] args) {
        App.ejecutar(AppTest.class, args);

    }

}

