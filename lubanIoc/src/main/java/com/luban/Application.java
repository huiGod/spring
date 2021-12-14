package com.luban;

import com.luban.app.Appconfig;
import com.luban.dao.Dao;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Application {

	public static void main(String[] args) {

		AnnotationConfigApplicationContext annotationConfigApplicationContext = new AnnotationConfigApplicationContext(
				Appconfig.class);

		//annotationConfigApplicationContext.refresh();

		Dao dao = annotationConfigApplicationContext.getBean(Dao.class);
		dao.query();
	}

}
