import { useEffect, useState, type ReactElement } from 'react';
import { Button, Drawer, Empty, Modal, Space, Tag, Typography, message } from 'antd';
import { DownloadOutlined, FileSearchOutlined } from '@ant-design/icons';
import { evidenceApi, type EvidenceResp } from '../services/claimApi';
import { resolveErrorMessage } from '@bone/shared-utils';
import { EvidenceImage, downloadEvidenceFile, isPreviewableImage } from './EvidenceImage';
import { docTypeLabel } from '../services/docTypes';

export interface EvidenceGalleryProps {
  claimId: string | null;
  open: boolean;
  onClose: () => void;
}

/** 工作台内嵌的影像件抽屉：服务端清单为准，缩略图点击放大，不做前端假数据。 */
export function EvidenceGallery({ claimId, open, onClose }: EvidenceGalleryProps): ReactElement {
  const [rows, setRows] = useState<EvidenceResp[]>([]);
  const [loading, setLoading] = useState(false);
  const [selected, setSelected] = useState<EvidenceResp | null>(null);

  useEffect(() => {
    if (!open || !claimId) {
      setRows([]);
      return;
    }
    let alive = true;
    setLoading(true);
    evidenceApi
      .list(claimId)
      .then((resp) => {
        if (alive) setRows(resp?.data ?? []);
      })
      .catch((err) => {
        if (alive) message.error(resolveErrorMessage(err, '影像件加载失败'));
      })
      .finally(() => {
        if (alive) setLoading(false);
      });
    return () => {
      alive = false;
    };
  }, [claimId, open]);

  return (
    <>
      <Drawer
        open={open}
        onClose={onClose}
        width={760}
        title={<span>影像件 · {claimId}</span>}
        destroyOnHidden
        data-testid="evidence-gallery"
      >
        <Typography.Text type="secondary">
          共 {rows.length} 份材料（来源：服务端证据清单）。缩略图点击放大，PDF 等材料可下载查看。
        </Typography.Text>
        <div style={{ marginTop: 16 }}>
          {rows.length === 0 && !loading ? (
            <Empty description="该案件暂无已登记影像件" />
          ) : (
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill,minmax(200px,1fr))', gap: 12 }}>
              {rows.map((row) => (
                <div
                  key={row.id}
                  role="button"
                  tabIndex={0}
                  data-testid={`evidence-gallery-item-${row.id}`}
                  onClick={() => setSelected(row)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') setSelected(row);
                  }}
                  style={{ border: '1px solid #f0f0f0', borderRadius: 6, padding: 8, cursor: 'pointer', background: '#fff' }}
                >
                  <div style={{ height: 120, display: 'flex', alignItems: 'center', justifyContent: 'center', background: '#fafafa', overflow: 'hidden' }}>
                    {isPreviewableImage(row.fileRef) ? (
                      <EvidenceImage fileRef={row.fileRef} style={{ maxHeight: '100%', maxWidth: '100%' }} />
                    ) : (
                      <FileSearchOutlined style={{ fontSize: 32, color: '#bfbfbf' }} />
                    )}
                  </div>
                  <div style={{ marginTop: 8 }}>
                    <Tag color="blue">{docTypeLabel(row.docType)}</Tag>
                    <Typography.Text type="secondary" style={{ fontSize: 12, wordBreak: 'break-all' }}>
                      {row.fileRef ?? row.id}
                    </Typography.Text>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </Drawer>
      <Modal
        open={Boolean(selected)}
        onCancel={() => setSelected(null)}
        footer={null}
        width={820}
        title={selected ? `${docTypeLabel(selected.docType)} · ${selected.fileRef}` : '影像预览'}
      >
        {selected && (
          <Space direction="vertical" style={{ width: '100%' }}>
            <div style={{ minHeight: 260, display: 'flex', alignItems: 'center', justifyContent: 'center', background: '#fafafa' }}>
              <EvidenceImage
                fileRef={selected.fileRef}
                style={{ maxWidth: '100%', maxHeight: '65vh' }}
                testId="evidence-gallery-preview"
              />
            </div>
            {selected.fileRef && (
              <Button
                icon={<DownloadOutlined />}
                onClick={() => void downloadEvidenceFile(selected.fileRef as string).catch((err) => message.error(resolveErrorMessage(err, '下载失败')))}
              >
                下载原件
              </Button>
            )}
          </Space>
        )}
      </Modal>
    </>
  );
}
