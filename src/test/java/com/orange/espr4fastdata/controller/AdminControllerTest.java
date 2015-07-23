/*
 * Copyright (C) 2015 Orange
 *
 * This software is distributed under the terms and conditions of the 'GNU GENERAL PUBLIC LICENSE
 * Version 2' license which can be found in the file 'LICENSE.txt' in this package distribution or
 * at 'http://www.gnu.org/licenses/gpl-2.0-standalone.html'.
 */

package com.orange.espr4fastdata.controller;

import com.espertech.esper.client.EventBean;
import com.orange.espr4fastdata.Application;
import com.orange.espr4fastdata.Init;
import com.orange.espr4fastdata.cep.ComplexEventProcessor;
import com.orange.espr4fastdata.exception.ConfigurationException;
import com.orange.espr4fastdata.exception.PersistenceException;
import com.orange.espr4fastdata.model.cep.Configuration;
import com.orange.espr4fastdata.persistence.Persistence;
import com.orange.espr4fastdata.util.Util;
import org.junit.*;
import org.junit.runner.RunWith;
import org.mockito.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.http.MockHttpOutputMessage;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.Assert.assertEquals;


/**
 * Test the Admin controller
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = {Application.class, AdminControllerTest.TestConfig.class})
@WebAppConfiguration
public class AdminControllerTest {

    @SpringBootApplication
    static class TestConfig {

        @Bean
        public ComplexEventProcessor complexEventProcessor() {
            return Mockito.mock(ComplexEventProcessor.class);
        }

        @Bean
        public Persistence persistence() {
            return Mockito.mock(Persistence.class);
        }
    }

    private MockMvc mockMvc;

    private Util util = new Util();

    @Autowired
    private MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ComplexEventProcessor complexEventProcessor;

    @Autowired
    private Persistence persistence;

    @Before
    public void setup() throws Exception {
        this.mockMvc = webAppContextSetup(webApplicationContext).build();
    }

    @After
    public void resetMocks() {
        reset(complexEventProcessor);
        reset(persistence);
    }

    @Test
    public void checkConfigurationNotFound() throws Exception {
        when(complexEventProcessor.getConfiguration()).thenReturn(null);

        mockMvc.perform(get("/v1/admin/config")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    public void postConfOK() throws Exception {
        Configuration configuration = util.getBasicConf();

        mockMvc.perform(post("/v1/admin/config").content(this.json(configuration)).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());

        ArgumentCaptor<Configuration> configurationArg = ArgumentCaptor.forClass(Configuration.class);
        verify(complexEventProcessor).setConfiguration(configurationArg.capture());

        Configuration capturedConfiguration = configurationArg.getValue();
        assertEquals(1, capturedConfiguration.getEventTypeIns().size());
        assertEquals("S.*", capturedConfiguration.getEventTypeIns().get(0).getId());
        assertEquals(1, capturedConfiguration.getEventTypeOuts().size());
        assertEquals("OUT1", capturedConfiguration.getEventTypeOuts().get(0).getId());

        ArgumentCaptor<Configuration> configurationArg2 = ArgumentCaptor.forClass(Configuration.class);
        verify(persistence).saveConfiguration(configurationArg2.capture());
        assertEquals(capturedConfiguration, configurationArg2.getValue());
    }

    @Test
    public void getConfiguration() throws Exception {
        Configuration configuration = util.getBasicConf();
        when(complexEventProcessor.getConfiguration()).thenReturn(configuration);

        mockMvc.perform(get("/v1/admin/config")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.in[0].id").value(configuration.getEventTypeIns().get(0).getId()))
                .andExpect(jsonPath("$.out[0].id").value(configuration.getEventTypeOuts().get(0).getId()))
                .andExpect(jsonPath("$.statements[0]").value(configuration.getStatements().get(0)));
    }

    @Test
    public void configurationErrorHandling() throws Exception {
        Configuration configuration = util.getBasicConf();

        doThrow(new ConfigurationException("ERROR", new Exception("DETAIL ERROR"))).when(complexEventProcessor).setConfiguration(any(Configuration.class));

        mockMvc.perform(post("/v1/admin/config").content(this.json(configuration)).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"))
                .andExpect(jsonPath("$.reasonPhrase").value("ERROR"))
                .andExpect(jsonPath("$.detail").value("DETAIL ERROR"));
    }

    @Test
    public void persistenceErrorHandling() throws Exception {

        doThrow(new PersistenceException("ERROR")).when(persistence).saveConfiguration(any(Configuration.class));

        Configuration configuration = util.getBasicConf();

        mockMvc.perform(post("/v1/admin/config").content(this.json(configuration)).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("500"))
                .andExpect(jsonPath("$.reasonPhrase").value("ERROR"));
    }

    protected String json(Object o) throws IOException {
        MockHttpOutputMessage mockHttpOutputMessage = new MockHttpOutputMessage();
        this.mappingJackson2HttpMessageConverter.write(
                o, MediaType.APPLICATION_JSON, mockHttpOutputMessage);
        return mockHttpOutputMessage.getBodyAsString();
    }
}
