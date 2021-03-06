package com.example.service.impl;

import java.time.Duration;

import com.example.controller.vm.UserVM;
import com.example.controller.vm.UsersStatisticVM;
import com.example.repository.MessageRepository;
import com.example.repository.UserRepository;
import com.example.service.StatisticService;
import com.example.service.gitter.dto.MessageResponse;
import com.example.service.impl.utils.MessageMapper;
import com.example.service.impl.utils.UserMapper;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DefaultStatisticService implements StatisticService {
    private static final UserVM EMPTY_USER = new UserVM("", "");

    private final UserRepository userRepository;
    private final MessageRepository messageRepository;

    @Autowired
    public DefaultStatisticService(UserRepository userRepository,
            MessageRepository messageRepository) {
        this.userRepository = userRepository;
        this.messageRepository = messageRepository;
    }

    @Override
    public Flux<UsersStatisticVM> updateStatistic(Flux<MessageResponse> messagesFlux) {
        return messagesFlux.map(MessageMapper::toDomainUnit)
                           .transform(messageRepository::saveAll)
                           .retryBackoff(Long.MAX_VALUE, Duration.ofMillis(500))
                           .onBackpressureLatest()
                           .concatMap(e -> this.doGetUserStatistic(), 1)
                           .errorStrategyContinue((t, e) -> {});
    }

    private Mono<UsersStatisticVM> doGetUserStatistic() {
        Mono<UserVM> topActiveUserMono = userRepository.findMostActive()
                                                       .map(UserMapper::toViewModelUnits)
                                                       .defaultIfEmpty(EMPTY_USER);

        Mono<UserVM> topMentionedUserMono = userRepository.findMostPopular()
                                                          .map(UserMapper::toViewModelUnits)
                                                          .defaultIfEmpty(EMPTY_USER);

        return Mono.zip(topActiveUserMono, topMentionedUserMono, UsersStatisticVM::new)
                   .timeout(Duration.ofSeconds(2));
    }
}
