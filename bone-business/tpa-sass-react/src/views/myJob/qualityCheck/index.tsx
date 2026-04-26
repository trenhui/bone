import React, { useEffect, useState } from 'react';
import { Card, Tabs } from 'antd';
import ProcessPageAPI, { ProcessListConfig } from '@/api/processPage';
import { ProcessPageCodeEnum } from '@/enums/process/ProcessPageCodeEnum';
import { DisplayModeEnum } from '@/enums/DisplayModeEnum';
import RenderEngine from '@/components/RenderEngine';

const { TabPane } = Tabs;

const Page: React.FC = () => {
  const [pageInfo, setPageInfo] = useState<ProcessListConfig>({
    modelNameList: [],
    pageBaseInfo: {
      id: '',
      code: '',
      name: '',
      description: '',
    },
    pageHead: {
      enablePageHead: 0,
      pageHeadField: [],
      pageHeadModelId: '',
    },
    pageBody: [],
    tableId: '',
    dataRange: 0,
    enableTab: 0,
    tabConditionList: [],
    uploadComponentList: [],
  });

  const [tabActiveName, setTabActiveName] = useState('全部');

  const getTableCondition = (tab: any = null) => {
    const res = [];

    res.push({
      field: 'stage',
      value: 'QUALITY_CHECK',
      type: 'EQUAL',
    });

    if (tab) {
      res.push({
        field: tab.field.bizCode,
        value: tab.fieldValue,
        type: 'EQUAL',
      });
    }
    return res;
  };

  const pageCode = ProcessPageCodeEnum.QUALITY_CHECK_LIST;

  const getPageInfo = async () => {
    try {
      const res = await ProcessPageAPI.getProcessListPage(pageCode);
      setPageInfo(res);
    } catch (error) {
      console.error('Failed to get page info:', error);
    }
  };

  useEffect(() => {
    getPageInfo();
  }, []);

  return (
    <div className="app-container">
      <Card className="mb-2" shadow={false}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div>
            <span style={{ fontWeight: 'bold', color: '#1890ff' }}>
              我的作业-质检
            </span>
            {pageInfo.pageBaseInfo?.description && (
              <div style={{ marginTop: '8px', fontSize: '14px', color: '#666' }}>
                {pageInfo.pageBaseInfo.description}
              </div>
            )}
          </div>
        </div>
      </Card>

      {pageInfo.enableTab ? (
        <Tabs activeKey={tabActiveName} onChange={setTabActiveName} type="bordered">
          <TabPane tab="全部" key="全部">
            <RenderEngine
              schema={pageInfo.pageBody?.[0]}
              displayMode={DisplayModeEnum.VIEW}
              pageData={{
                pageId: pageInfo.pageBaseInfo?.id,
                pageCode: pageInfo.pageBaseInfo?.code,
                tableCondition: getTableCondition(),
              }}
            />
          </TabPane>
          {pageInfo.tabConditionList.map((tab, index) => (
            <TabPane key={index} tab={tab.title} tabKey={tab.title}>
              <RenderEngine
                schema={pageInfo.pageBody?.[0]}
                displayMode={DisplayModeEnum.VIEW}
                pageData={{
                  pageId: pageInfo.pageBaseInfo?.id,
                  pageCode: pageInfo.pageBaseInfo?.code,
                  tableCondition: getTableCondition(tab),
                }}
              />
            </TabPane>
          ))}
        </Tabs>
      ) : (
        <Card shadow={false}>
          <RenderEngine
            schema={pageInfo.pageBody?.[0]}
            displayMode={DisplayModeEnum.VIEW}
            pageData={{
              pageId: pageInfo.pageBaseInfo?.id,
              pageCode: pageInfo.pageBaseInfo?.code,
              tableCondition: getTableCondition(),
            }}
          />
        </Card>
      )}
    </div>
  );
};

export default Page;
