create table if not exists `tpa-saas`.ss_claim
(
    id                 bigint      not null primary key,
    tenant_id          bigint      not null comment '租户id',
    biz_identity_code  varchar(32) not null comment '业务身份id',
    related_id         bigint      null comment '关联id',
    claim_no           varchar(64) not null comment '赔案号',
    batch_no           varchar(64) null comment '批次号',
    policy_no          varchar(64) null comment '保单号',
    status             varchar(16) null comment '当前状态',
    stage              varchar(16) null comment '当前阶段',
    emergency          varchar(16) null comment '重要紧急',
    source             varchar(16) null comment '赔案来源',
    biz_type           varchar(16) null comment '业务类型',
    process_type       varchar(16) null comment '流程类型',
    counting_result    int         null comment '清点张数',
    image_upload_flag  varchar(8)  null comment '影像是否上传',
    image_count        int         null comment '影像数量',
    image_upload_count int         null comment '影像上传次数',
    image_upload_time  datetime    null comment '上传时间',
    image_uploader     bigint      null comment '影像操作人员',
    insurer_claim_no   varchar(64) null comment '保司报案号',
    insurer_request_no varchar(64) null comment '保司申请号',
    insurer_batch_no   varchar(64) null comment '保司批次号',
    insurer_receipt_no varchar(64) null comment '保司收单号',
    insurer_policy_no  varchar(64) null comment '保司保单号',
    out_insure_time    datetime    null comment '出险时间',
    out_insure_type    varchar(16) null comment '出险类型',
    out_insure_address varchar(64) null comment '出险地点',
    create_time        datetime    not null comment '创建时间',
    create_by          bigint      not null comment '创建人',
    update_time        datetime    not null comment '修改时间',
    update_by          bigint      not null comment '修改人',
    deleted            int         not null comment '逻辑删除',
    constraint claim_no_UNIQUE
        unique (claim_no)
)
    comment 'tpa-saas赔案信息表';

create table if not exists `tpa-saas`.ss_claim_image
(
    id          bigint           not null
        primary key,
    tenant_id   bigint           not null comment '租户id',
    related_id  bigint           not null comment '关联赔案id',
    image_path  varchar(256)     not null comment '影像件路径',
    image_name  varchar(64)      not null comment '影像件名称',
    image_type  varchar(16)      null comment '影像件类型',
    clear_type  varchar(16)      null comment '清晰类型',
    angle       int              null comment '旋转角度',
    remark      longtext         null comment '备注',
    push_flag   int(1) default 1 null comment '影像件是否推送标识(默认1-推送 0-不推送)',
    create_time datetime         not null comment '创建时间',
    create_by   bigint           not null comment '创建人',
    update_time datetime         not null comment '修改时间',
    update_by   bigint           not null comment '修改人',
    deleted     int              not null comment '逻辑删除'
)
    comment 'tpa-saas赔案影像件表';

create table if not exists `tpa-saas`.ss_claim_invoice
(
    id                       bigint         not null
        primary key,
    tenant_id                bigint         not null comment '租户id',
    biz_identity_code        varchar(32)    not null comment '业务身份code',
    related_id               bigint         not null comment '关联赔案id',
    invoice_no               varchar(64)    null comment '票据代码',
    claim_invoice_id         bigint         null comment '票据id',
    digital_flag             varchar(8)     null comment '是否电子发票',
    e_invoice_no             varchar(64)    null comment '电子发票号',
    verification_code        varchar(64)    null comment '票据验证码',
    invoice_date             date           null comment '开票日期',
    invoice_type             varchar(16)    null comment '发票类型',
    bill_type                varchar(16)    null comment '票据类型',
    visit_type               varchar(16)    null comment '就诊类型',
    invoice_name             varchar(16)    null comment '发票姓名',
    medical_institution_type varchar(16)    null comment '医疗机构类型',
    yb_type                  varchar(16)    null comment '医保类型',
    hospital_name            varchar(32)    null comment '医院名称',
    hospital_level           varchar(16)    null comment '医院等级',
    hospital_type            varchar(16)    null comment '医院性质',
    diagnosis                varchar(256)   null comment '诊断',
    severe_flag              varchar(8)     null comment '是否重疾',
    severe_name              varchar(32)    null comment '重疾名称',
    hospital_properties      varchar(256)   null comment '医院特征',
    remark                   varchar(256)   null comment '发票备注',
    visit_date               date           null comment '就诊日期',
    hospital_period          varchar(32)    null comment '住院期间',
    hospital_department      varchar(32)    null comment '住院科别',
    hospital_days            int            null comment '住院天数',
    subsidy_days             int            null comment '津贴天数',
    total_amount             decimal(14, 2) null comment '发票总费用',
    minimum_standard         int            null comment '起付标准',
    part_self_pay_amount     decimal(14, 2) null comment '部分自费',
    self_pay_amount          decimal(14, 2) null comment '个人自费',
    reasonable_amount        decimal(14, 2) null comment '合理金额',
    unreasonable_amount      decimal(14, 2) null comment '不合理金额',
    yb_pooling_amount        decimal(14, 2) null comment '医保统筹金额',
    third_party_pay_amount   int            null comment '三方支付',
    account_no               varchar(64)    null comment '个人账户',
    other_reimbursement      int            null comment '其他报销',
    create_time              datetime       not null comment '创建时间',
    create_by                bigint         not null comment '创建人',
    update_time              datetime       not null comment '修改时间',
    update_by                bigint         not null comment '修改人',
    deleted                  int            not null comment '逻辑删除'
);

create index claim_id
    on `tpa-saas`.ss_claim_invoice (related_id);

create table if not exists `tpa-saas`.ss_claim_stakeholder
(
    id                            bigint       not null
        primary key,
    tenant_id                     bigint       not null comment '租户id',
    biz_identity_code             varchar(32)  not null comment '业务身份id',
    related_id                    varchar(64)  not null comment '关联赔案id',
    person_type                   varchar(16)  not null comment '相关人类型',
    name                          varchar(16)  null comment '姓名',
    gender                        varchar(16)  null comment '性别',
    birthday                      varchar(16)  null comment '出生年月',
    identity_type                 varchar(16)  null comment '证件类型',
    identity_no                   varchar(32)  null comment '证件号',
    identity_date_period          varchar(32)  null comment '证件有效期',
    occupation                    varchar(32)  null comment '职业',
    nationality                   varchar(32)  null comment '国籍',
    phone                         varchar(32)  null comment '联系方式',
    contact_address               varchar(64)  null comment '联系地址',
    relation_to_out_insure        varchar(16)  null comment '与出险人的关系',
    relation_to_main_insure       varchar(16)  null comment '与主被保人的关系',
    relation_to_benefit           varchar(16)  null comment '与受益人的关系',
    benefit_percentage            varchar(16)  null comment '受益比例',
    related_benefit_id            bigint       null comment '关联的受益人',
    collect_type                  varchar(16)  null comment '领款人类型',
    business_name                 varchar(32)  null comment '单位名称',
    business_identity_type        varchar(16)  null comment '单位证件类型',
    business_identity_disc        varchar(128) null comment '单位证件描述',
    business_identity_no          varchar(64)  null comment '单位证件号码',
    business_identity_date_period varchar(32)  null comment '单位证件有效期',
    business_place                varchar(64)  null comment '单位经营场所',
    business_range                varchar(64)  null comment '单位经营范围',
    transfer_method_type          varchar(16)  null comment '转账方式',
    payment_method_type           varchar(16)  null comment '给付方式',
    account_no                    varchar(64)  null comment '银行账号',
    bank_code                     varchar(32)  null comment '开户行',
    branch_code                   varchar(32)  null comment '开户行分行',
    create_time                   datetime     not null comment '创建时间',
    create_by                     bigint       not null comment '创建人',
    update_time                   datetime     not null comment '修改时间',
    update_by                     bigint       not null comment '修改人',
    deleted                       int          not null comment '逻辑删除'
);

create index claim_id
    on `tpa-saas`.ss_claim_stakeholder (related_id);

create index identity_no
    on `tpa-saas`.ss_claim_stakeholder (identity_no);

create table if not exists `tpa-saas`.ss_claim_track_log
(
    id               bigint       not null
        primary key,
    tenant_id        bigint       not null comment '租户id',
    related_claim_id bigint       not null comment '关联赔案id',
    stage            varchar(16)  not null comment '当前阶段',
    type             varchar(16)  not null comment '操作类型',
    before_value     longtext     null comment '更新前数据',
    after_value      longtext     null comment '更新后数据',
    message          varchar(128) null comment '操作描述',
    remark           varchar(256) null comment '操作备注',
    operator         bigint       null,
    create_time      datetime     not null comment '创建时间',
    create_by        bigint       not null comment '创建人',
    update_time      datetime     not null comment '修改时间',
    update_by        bigint       not null comment '修改人',
    deleted          int          not null comment '逻辑删除'
);

create index sign_id
    on `tpa-saas`.ss_claim_track_log (related_claim_id);

create table if not exists `tpa-saas`.ss_file_upload_record
(
    id            bigint       not null
        primary key,
    tenant_id     bigint       not null comment '租户id',
    related_model varchar(64)  not null comment '关联模型名',
    related_id    bigint       not null comment '关联表id',
    upload_scene  varchar(32)  null comment '上传场景',
    file_type     varchar(16)  not null comment '文件类型',
    file_path     varchar(256) null comment '文件路径',
    file_name     varchar(64)  not null comment '文件名称',
    success       int(1)       null comment '是否成功上传',
    status        varchar(64)  null comment '当前状态',
    check_type    varchar(16)  null comment '校验方式',
    remark        longtext     null comment '备注',
    create_time   datetime     not null comment '创建时间',
    create_by     bigint       not null comment '创建人',
    update_time   datetime     not null comment '修改时间',
    update_by     bigint       not null comment '修改人',
    deleted       int          not null comment '逻辑删除'
);

create table if not exists `tpa-saas`.ss_invoice_project
(
    id                             bigint      not null
        primary key,
    tenant_id                      bigint      not null comment '租户id',
    biz_identity_code              varchar(32) not null comment '业务身份id',
    related_id                     bigint      not null comment '关联发票id',
    project_name                   varchar(32) not null comment '项目名称',
    invoice_amount                 int         null comment '项目发票金额',
    project_self_pay_amount        int         null comment '项目自费金额',
    project_part_self_pay_amount   int         null comment '项目部分自费',
    pooling_amount                 int         null comment '项目统筹金额',
    project_third_party_pay_amount int         null comment '项目三方支付',
    project_reasonable_amount      int         null comment '合理金额',
    project_unreasonable_amount    int         null comment '不合理金额',
    create_time                    datetime    not null comment '创建时间',
    create_by                      bigint      not null comment '创建人',
    update_time                    datetime    not null comment '修改时间',
    update_by                      bigint      not null comment '修改人',
    deleted                        int         not null comment '逻辑删除'
);

create index invoice_id
    on `tpa-saas`.ss_invoice_project (related_id);

create table if not exists `tpa-saas`.ss_invoice_project_item
(
    id                   bigint         not null
        primary key,
    tenant_id            bigint         not null comment '租户id',
    biz_identity_code    varchar(32)    not null comment '业务身份id',
    related_id           bigint         not null comment '关联项目id',
    related_project_name varchar(32)    null comment '关联项目名称',
    item_name            varchar(32)    null comment '药品诊疗名称',
    type                 varchar(16)    null comment '类型',
    charging_percentage  varchar(16)    null comment '扣费比例',
    price                decimal(14, 2) null comment '单价',
    count                int            null comment '数量',
    item_total_amount    decimal(14, 2) null comment '总价',
    charging_amount      decimal(14, 2) null comment '扣费金额',
    dosage_form          varchar(32)    null comment '剂型',
    create_time          datetime       not null comment '创建时间',
    create_by            bigint         not null comment '创建人',
    update_time          datetime       not null comment '修改时间',
    update_by            bigint         not null comment '修改人',
    deleted              int            not null comment '逻辑删除'
);

create index project_id
    on `tpa-saas`.ss_invoice_project_item (related_id);

create table if not exists `tpa-saas`.ss_sign_record
(
    id                   bigint       not null
        primary key,
    tenant_id            bigint       not null comment '租户id',
    biz_identity_code    varchar(32)  null comment '业务身份id',
    batch_no             varchar(64)  not null comment '批次号',
    sign_type            varchar(16)  null comment '签收方式',
    sign_channel         varchar(16)  null comment '签收渠道',
    biz_type             varchar(16)  null comment '业务类型',
    process_type         varchar(16)  null comment '流程类型',
    claim_count          int          null comment '赔案数',
    image_upload_flag    varchar(8)   null comment '是否有上传影像',
    insurance_company    varchar(64)  null comment '保险公司',
    insurance_subsidiary varchar(64)  null comment '保险分公司',
    insuring_agency      varchar(64)  null comment '投保公司',
    delivery_no          varchar(64)  null comment '收单流水号',
    delivery_time        datetime     null comment '收件时间',
    express_no           varchar(64)  null comment '快递编号',
    express_company      varchar(16)  null comment '快递公司',
    arrive_time          datetime     null comment '快递到达时间',
    send_from            varchar(64)  null comment '发件地',
    sender               varchar(32)  null comment '发件人',
    sender_contact       varchar(64)  null comment '发件人联系方式',
    emergency            varchar(16)  null comment '紧急程度',
    remark               varchar(256) null comment '备注',
    sign_time            datetime     null comment '签收时间',
    sign_institution     varchar(64)  null comment '签收机构',
    sign_operator        varchar(32)  null comment '签收操作人员',
    sign_status          varchar(32)  null comment '签收状态',
    create_time          datetime     not null comment '创建时间',
    create_by            bigint       not null comment '创建人',
    update_time          datetime     not null comment '修改时间',
    update_by            bigint       not null comment '修改人',
    deleted              int          not null comment '逻辑删除',
    constraint batch_no_UNIQUE
        unique (batch_no)
);

create table if not exists `tpa-saas`.ss_sign_record_track_log
(
    id                     bigint       not null
        primary key,
    tenant_id              bigint       not null comment '租户id',
    related_sign_record_id bigint       not null comment '关联签收记录id',
    sign_status            varchar(32)  not null comment '签收状态',
    type                   varchar(16)  not null comment '操作类型',
    message                varchar(128) null comment '操作描述',
    remark                 varchar(256) null comment '操作备注',
    create_time            datetime     not null comment '创建时间',
    create_by              bigint       not null comment '创建人',
    update_time            datetime     not null comment '修改时间',
    update_by              bigint       not null comment '修改人',
    deleted                int          not null comment '逻辑删除'
);

create index sign_id
    on `tpa-saas`.ss_sign_record_track_log (related_sign_record_id);

