package es.andrewazor.containertest.commands.internal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openjdk.jmc.common.unit.IConstrainedMap;
import org.openjdk.jmc.flightrecorder.configuration.events.EventOptionID;
import org.openjdk.jmc.flightrecorder.configuration.recording.RecordingOptionsBuilder;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import es.andrewazor.containertest.JMCConnection;
import es.andrewazor.containertest.RecordingExporter;
import es.andrewazor.containertest.StdoutTest;

@ExtendWith(MockitoExtension.class)
class StartRecordingCommandTest extends StdoutTest {

    private StartRecordingCommand command;
    @Mock private JMCConnection connection;
    @Mock private IFlightRecorderService service;
    @Mock private RecordingExporter exporter;
    @Mock private EventOptionsBuilder.Factory eventOptionsBuilderFactory;
    @Mock private RecordingOptionsBuilderFactory recordingOptionsBuilderFactory;

    @BeforeEach
    void setup() {
        command = new StartRecordingCommand(exporter, eventOptionsBuilderFactory, recordingOptionsBuilderFactory);
        command.connectionChanged(connection);
    }

    @Test
    void shouldBeNamedStart() {
        MatcherAssert.assertThat(command.getName(), Matchers.equalTo("start"));
    }

    @Test
    void shouldNotValidateZeroArgs() {
        assertFalse(command.validate(new String[0]));
        MatcherAssert.assertThat(stdout.toString(), Matchers.equalTo("Expected two arguments: recording name and event types.\n"));
    }

    @Test
    void shouldNotValidateOneArg() {
        assertFalse(command.validate(new String[1]));
        MatcherAssert.assertThat(stdout.toString(), Matchers.equalTo("Expected two arguments: recording name and event types.\n"));
    }

    @Test
    void shouldNotValidateTooManyArgs() {
        assertFalse(command.validate(new String[3]));
        MatcherAssert.assertThat(stdout.toString(), Matchers.equalTo("Expected two arguments: recording name and event types.\n"));
    }

    @Test
    void shouldNotValidateBadRecordingName() {
        assertFalse(command.validate(new String[]{ ".", "foo.Bar:enabled=true" }));
        MatcherAssert.assertThat(stdout.toString(), Matchers.equalTo(". is an invalid recording name\n"));
    }

    @Test
    void shouldNotValidateBadEventString() {
        assertFalse(command.validate(new String[]{ "foo", "foo.Bar:=true" }));
        MatcherAssert.assertThat(stdout.toString(), Matchers.equalTo("foo.Bar:=true is an invalid events pattern\n"));
    }

    @Test
    void shouldValidateArgs() {
        assertTrue(command.validate(new String[]{ "foo", "foo.Bar:enabled=true" }));
        MatcherAssert.assertThat(stdout.toString(), Matchers.emptyString());
    }

    @Test
    void shouldStartRecordingOnExecute() throws Exception {
        when(connection.getService()).thenReturn(service);
        when(service.getAvailableRecordings()).thenReturn(Collections.emptyList());
        IConstrainedMap<String> recordingOptions = mock(IConstrainedMap.class);
        RecordingOptionsBuilder recordingOptionsBuilder = mock(RecordingOptionsBuilder.class);
        when(recordingOptionsBuilderFactory.create(Mockito.any())).thenReturn(recordingOptionsBuilder);
        when(recordingOptionsBuilder.build()).thenReturn(recordingOptions);
        when(recordingOptionsBuilder.name(Mockito.any())).thenReturn(recordingOptionsBuilder);
        EventOptionsBuilder builder = mock(EventOptionsBuilder.class);
        when(eventOptionsBuilderFactory.create(Mockito.any())).thenReturn(builder);
        IConstrainedMap<EventOptionID> events = mock(IConstrainedMap.class);
        when(builder.build()).thenReturn(events);

        IRecordingDescriptor descriptor = mock(IRecordingDescriptor.class);
        when(service.start(Mockito.any(), Mockito.any())).thenReturn(descriptor);

        verifyZeroInteractions(connection);
        verifyZeroInteractions(service);
        verifyZeroInteractions(exporter);

        command.execute(new String[]{ "foo", "foo.Bar:enabled=true" });

        ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<IConstrainedMap<String>> recordingOptionsCaptor = ArgumentCaptor.forClass(IConstrainedMap.class);
        ArgumentCaptor<IConstrainedMap<EventOptionID>> eventsCaptor = ArgumentCaptor.forClass(IConstrainedMap.class);
        ArgumentCaptor<IRecordingDescriptor> descriptorCaptor = ArgumentCaptor.forClass(IRecordingDescriptor.class);
        verify(recordingOptionsBuilder).name(nameCaptor.capture());
        verify(service).getAvailableRecordings();
        verify(service).start(recordingOptionsCaptor.capture(), eventsCaptor.capture());
        verify(exporter).addRecording(descriptorCaptor.capture());

        String actualName = nameCaptor.getValue();
        IConstrainedMap<String> actualRecordingOptions = recordingOptionsCaptor.getValue();
        IConstrainedMap<EventOptionID> actualEvents = eventsCaptor.getValue();
        IRecordingDescriptor recordingDescriptor = descriptorCaptor.getValue();

        MatcherAssert.assertThat(actualName, Matchers.equalTo("foo"));
        MatcherAssert.assertThat(recordingDescriptor, Matchers.sameInstance(descriptor));
        MatcherAssert.assertThat(actualEvents, Matchers.sameInstance(events));
        MatcherAssert.assertThat(actualRecordingOptions, Matchers.sameInstance(recordingOptions));
        ArgumentCaptor<String> eventCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> optionCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(builder).addEvent(eventCaptor.capture(), optionCaptor.capture(), valueCaptor.capture());
        verify(builder).build();

        MatcherAssert.assertThat(eventCaptor.getValue(), Matchers.equalTo("foo.Bar"));
        MatcherAssert.assertThat(optionCaptor.getValue(),Matchers.equalTo("enabled"));
        MatcherAssert.assertThat(valueCaptor.getValue(), Matchers.equalTo("true"));

        verifyNoMoreInteractions(connection);
        verifyNoMoreInteractions(service);
        verifyNoMoreInteractions(exporter);
    }

    @Test
    void shouldHandleNameCollisionOnExecute() throws Exception {
        IRecordingDescriptor existingRecording = mock(IRecordingDescriptor.class);
        when(existingRecording.getName()).thenReturn("foo");

        when(connection.getService()).thenReturn(service);
        when(service.getAvailableRecordings()).thenReturn(Arrays.asList(existingRecording));

        verifyZeroInteractions(connection);
        verifyZeroInteractions(service);
        verifyZeroInteractions(exporter);

        command.execute(new String[]{ "foo", "foo.Bar:enabled=true" });

        verify(service).getAvailableRecordings();
        MatcherAssert.assertThat(stdout.toString(), Matchers.equalTo("Recording with name \"foo\" already exists\n"));

        verifyNoMoreInteractions(connection);
        verifyNoMoreInteractions(service);
        verifyNoMoreInteractions(exporter);
    }

}