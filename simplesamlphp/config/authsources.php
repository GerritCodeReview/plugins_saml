<?php

$config = array(
    'admin' => array(
        'core:AdminPassword',
    ),

    'example-userpass' => array(
        'exampleauth:UserPass',
        'user1:user1pass' => array(
            'uid' => array('1'),
            'username' => 'user1',
            'first_name' => 'User',
            'last_name' => 'One',
            'email' => 'user_1@example.com',
        ),
        'user2:user2pass' => array(
            'uid' => array('2'),
            'username' => 'user2',
            'first_name' => 'User',
            'last_name' => 'Two',
            'email' => 'user_2@example.com',
        ),
        'user3:user3pass' => array(
            'uid' => array('3'),
            'username' => 'user3',
            'first_name' => 'User',
            'last_name' => 'Three',
            'email' => 'user_3@example.com',
        ),
        'user4:user4pass' => array(
            'uid' => array('4'),
            'username' => 'user4',
            'name' => 'User Four',
            'email' => 'user_4@example.com',
        ),
    ),
);
